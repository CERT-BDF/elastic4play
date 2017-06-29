package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.field
import com.sksamuel.elastic4s.mappings.FieldType.{ LongType, ObjectType }
import com.sksamuel.elastic4s.mappings.ObjectFieldDefinition
import org.elastic4play.AttributeError
import org.elastic4play.controllers.{ InputValue, JsonInputValue }
import org.scalactic._
import org.scalactic.Accumulation._
import play.api.libs.json.{ JsNull, JsNumber, JsObject, JsValue }

object MetricsAttributeFormat extends AttributeFormat[JsValue]("metrics") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, Every[AttributeError]] = fromInputValue(subNames, JsonInputValue(value))

  override def fromInputValue(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] = {
    if (subNames.isEmpty) {
      value match {
        case JsonInputValue(v: JsObject) ⇒
          v.fields
            .validatedBy {
              case (_, _: JsNumber) ⇒ Good(())
              case (_, JsNull)      ⇒ Good(())
              case _                ⇒ formatError(value)
            }
            .map(_ ⇒ v)
        case _ ⇒ formatError(value)
      }
    }
    else {
      OptionalAttributeFormat(NumberAttributeFormat).inputValueToJson(subNames.tail, value) //.map(v => JsObject(Seq(subNames.head -> v)))
    }
  }

  override def elasticType(attributeName: String): ObjectFieldDefinition = field(attributeName, ObjectType).as(field("_default_", LongType))
}