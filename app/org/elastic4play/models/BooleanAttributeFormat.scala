package org.elastic4play.models

import com.sksamuel.elastic4s.ElasticDsl.field
import com.sksamuel.elastic4s.mappings.BooleanFieldDefinition
import com.sksamuel.elastic4s.mappings.FieldType.BooleanType
import org.elastic4play.controllers.{ InputValue, JsonInputValue, StringInputValue }
import org.elastic4play.{ AttributeError, InvalidFormatAttributeError }
import org.scalactic._
import play.api.libs.json.{ JsBoolean, JsValue }

object BooleanAttributeFormat extends AttributeFormat[Boolean]("boolean") {
  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case _: JsBoolean if subNames.isEmpty ⇒ Good(value)
    case _                                ⇒ formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Boolean Or Every[AttributeError] = {
    if (subNames.nonEmpty)
      formatError(value)
    else
      value match {
        case StringInputValue(Seq(v)) ⇒ try {
          Good(v.toBoolean)
        }
        catch {
          case _: Throwable ⇒ formatError(value)
        }
        case JsonInputValue(JsBoolean(v)) ⇒ Good(v)
        case _                            ⇒ formatError(value)
      }
  }

  override def elasticType(attributeName: String): BooleanFieldDefinition = field(attributeName, BooleanType)
}