package org.elastic4play.database

import scala.concurrent.{blocking, ExecutionContext, Future}
import play.api.{Configuration, Logger}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest
import javax.inject.{Inject, Singleton}
import org.elastic4play.InternalError
import org.elastic4play.models.{ChildModelDef, ModelAttributes}
import org.elastic4play.utils.Collection

@Singleton
class DBIndex(db: DBConfiguration, nbShards: Int, nbReplicas: Int, settings: Map[String, Any], implicit val ec: ExecutionContext) {

  @Inject def this(configuration: Configuration, db: DBConfiguration, ec: ExecutionContext) =
    this(
      db,
      configuration.getOptional[Int]("search.nbshards").getOrElse(5),
      configuration.getOptional[Int]("search.nbreplicas").getOrElse(1),
      configuration
        .getOptional[Configuration]("search.settings")
        .fold(Map.empty[String, Any]) { settings ⇒
          settings
            .entrySet
            .toMap
            .mapValues(_.unwrapped)
        },
      ec
    )

  private[DBIndex] lazy val logger = Logger(getClass)

  /**
    * Create a new index. Collect mapping for all attributes of all entities
    *
    * @param models list of all ModelAttributes to used in order to build index mapping
    * @return a future which is completed when index creation is finished
    */
  def createIndex(models: Iterable[ModelAttributes]): Future[Unit] = {
    val mappingTemplates = Collection.distinctBy(models.flatMap(_.attributes).flatMap(_.elasticTemplate()))(_.name)
    val fields           = models.flatMap(_.attributes.filterNot(_.attributeName == "_id").map(_.elasticMapping)).toSeq
    val relationsField = models
      .map {
        case child: ChildModelDef[_, _, _, _] ⇒ child.parentModel.modelName → Seq(child.modelName)
        case model                            ⇒ model.modelName             → Nil
      }
      .groupBy(_._1)
      .foldLeft(joinField("relations")) {
        case (join, (parent, child)) ⇒ join.relation(parent, child.flatMap(_._2).toSeq)
      }

    for {
      majorVersion ← nodeMajorVersion
      modelMapping = properties(fields :+ relationsField)
        .dateDetection(false)
        .numericDetection(false)
        .templates(mappingTemplates)
      createIndexRequest = CreateIndexRequest(db.indexName)
        .mapping(modelMapping)
        .shards(nbShards)
        .replicas(nbReplicas)
      createIndexRequestWithSettings = majorVersion match {
        case 5 ⇒ createIndexRequest.indexSetting("mapping.single_type", true)
        case _ ⇒ createIndexRequest
      }
      _ ← db.execute {
        settings.foldLeft(createIndexRequestWithSettings) {
          case (cid, (key, value)) ⇒ cid.indexSetting(key, value)
        }
      }
    } yield ()
  }

  /**
    * Tests whether the index exists
    *
    * @return future of true if the index exists
    */
  def getIndexStatus: Future[Boolean] =
    db.execute {
        indexExists(db.indexName)
      }
      .map {
        _.isExists
      }

  /**
    * Tests whether the index exists
    *
    * @return true if the index exists
    */
  def indexStatus: Boolean = blocking {
    getIndexStatus.await
  }

  /**
    * Get the number of document of this type
    *
    * @param modelName name of the document type from which the count must be done
    * @return document count
    */
  def getSize(modelName: String): Future[Long] =
    db.execute {
        search(db.indexName).matchQuery("relations", modelName).size(0)
      }
      .map {
        _.totalHits
      }
      .recover { case _ ⇒ 0L }

  /**
    * Get cluster status:
    * 0: green
    * 1: yellow
    * 2: red
    *
    * @return cluster status
    */
  def getClusterStatus: Future[Int] =
    db.execute {
        clusterHealth(db.indexName)
      }
      .map {
        _.status match {
          case "green"  ⇒ 0
          case "yellow" ⇒ 1
          case "red"    ⇒ 2
          case status ⇒
            logger.error(s"unknown cluster status: $status")
            2
        }
      }
      .recover { case _ ⇒ 2 }

  def nodeVersions: Future[Seq[String]] =
    db.execute {
        nodeInfo()
      }
      .map(_.nodes.values.map(_.version).toSeq.distinct)

  def nodeMajorVersion: Future[Int] =
    nodeVersions.flatMap { v ⇒
      val majorVersions = v.map(_.takeWhile(_ != '.')).distinct.map(_.toInt)
      if (majorVersions.size == 1)
        Future.successful(majorVersions.head)
      else
        Future.failed(InternalError(s"The ElasticSearch cluster contains node with different major versions ($v)"))
    }

  def clusterStatus: Int = blocking {
    getClusterStatus.await
  }

  def getClusterStatusName: Future[String] = getClusterStatus.map {
    case 0 ⇒ "OK"
    case 1 ⇒ "WARNING"
    case 2 ⇒ "ERROR"
    case _ ⇒ "UNKNOWN"
  }

  def clusterStatusName: String = blocking {
    getClusterStatusName.await
  }
}
