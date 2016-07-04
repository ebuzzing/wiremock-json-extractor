package tv.teads.wiremock.extension

import java.io.{StringReader, StringWriter}

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import freemarker.template.{Configuration, _}

import scala.collection.JavaConverters._
import scala.util.Try

class FreeMarkerRenderer extends ResponseDefinitionTransformer {

  override val getName: String = "freemarker-renderer"

  override val applyGlobally: Boolean = false

  private val mapper: ObjectMapper = new ObjectMapper
  private val configuration: Configuration = new Configuration(Configuration.VERSION_2_3_24)
  private val wrapper: ObjectWrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_24).build()

  override def transform(
    request:            Request,
    responseDefinition: ResponseDefinition,
    files:              FileSource,
    parameters:         Parameters
  ): ResponseDefinition = {
    Try {
      val requestBody: JsonNode = mapper.readTree(request.getBodyAsString)
      val template = new Template("template", new StringReader(responseDefinition.getBody), configuration)

      val writer = new StringWriter
      template.process(json2hash(wrapper, requestBody), writer)
      val body = writer.toString

      ResponseDefinitionBuilder
        .like(responseDefinition)
        .withBody(body)
        .build()

    }.getOrElse(responseDefinition)
  }

  private def json2hash(wrapper: ObjectWrapper, node: JsonNode): SimpleHash = {
    val hash = new SimpleHash(wrapper)
    hash.put("$", json2template(wrapper, node))
    hash
  }

  private def json2template(wrapper: ObjectWrapper, node: JsonNode): TemplateModel = node match {
    // Values JsonNode
    case _ if node.isBigDecimal          ⇒ wrapper.wrap(node.decimalValue())
    case _ if node.isBigInteger          ⇒ wrapper.wrap(node.bigIntegerValue())
    case _ if node.isBinary              ⇒ wrapper.wrap(node.binaryValue())
    case _ if node.isBoolean             ⇒ wrapper.wrap(node.booleanValue())
    case _ if node.isDouble              ⇒ wrapper.wrap(node.doubleValue())
    case _ if node.isFloat               ⇒ wrapper.wrap(node.floatValue())
    case _ if node.isFloatingPointNumber ⇒ wrapper.wrap(node.decimalValue())
    case _ if node.isInt                 ⇒ wrapper.wrap(node.intValue())
    case _ if node.isIntegralNumber      ⇒ wrapper.wrap(node.intValue())
    case _ if node.isLong                ⇒ wrapper.wrap(node.longValue())
    case _ if node.isMissingNode         ⇒ wrapper.wrap(null)
    case _ if node.isNull                ⇒ wrapper.wrap(null)
    case _ if node.isNumber              ⇒ wrapper.wrap(node.numberValue())
    case _ if node.isShort               ⇒ wrapper.wrap(node.shortValue())
    case _ if node.isTextual             ⇒ wrapper.wrap(node.textValue())

    // Container JsonNode
    case _ if node.isArray ⇒
      val seq = new SimpleSequence(wrapper)
      node.elements().asScala.foreach(elem ⇒ seq.add(json2template(wrapper, elem)))
      seq
    case _ if node.isObject ⇒
      val hash = new SimpleHash(wrapper)
      node.fields().asScala.foreach(field ⇒ hash.put(field.getKey, json2template(wrapper, field.getValue)))
      hash
  }

}