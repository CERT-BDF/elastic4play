package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.field
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.mappings.StringFieldDefinition
import org.elastic4play.controllers.{ InputValue, JsonInputValue, StringInputValue }
import org.elastic4play.{ AttributeError, InvalidFormatAttributeError }
import org.scalactic._
import play.api.libs.json.{ Format, JsString, JsValue }

import scala.reflect.ClassTag

case class EnumerationAttributeFormat[T <: Enumeration](enum: T)(implicit tag: ClassTag[T], format: Format[T#Value])
    extends AttributeFormat[T#Value](s"enumeration") {

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case JsString(v) if subNames.isEmpty ⇒ try {
      enum.withName(v); Good(value)
    }
    catch {
      case _: Throwable ⇒ formatError(JsonInputValue(value))
    }
    case _ ⇒ formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): T#Value Or Every[AttributeError] = {
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v)) ⇒ try {
          Good(enum.withName(v))
        }
        catch {
          case _: Throwable ⇒ formatError(value)
        }
        case JsonInputValue(JsString(v)) ⇒ try {
          Good(enum.withName(v))
        }
        catch {
          case _: Throwable ⇒ formatError(value)
        }
        case _ ⇒ formatError(value)
      }
  }

  override def elasticType(attributeName: String): StringFieldDefinition = field(attributeName, StringType) index "not_analyzed"
}