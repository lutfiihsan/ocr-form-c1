package id.kawalc1.clients

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.{ Http, HttpExt, HttpsConnectionContext }
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.json4s.Formats
import org.json4s.native.Serialization.read

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.Manifest
import scala.util.{ Failure, Success, Try }

trait HttpClientSupport extends LazyLogging {

  def credentialsProvider: Future[Option[Authorization]] = Future.successful(None)

  implicit val system: ActorSystem
  implicit val mat: Materializer
  lazy val http: HttpExt = Http()

  def SecurityContext: HttpsConnectionContext = http.defaultClientHttpsContext

  def parseJson[A: Manifest](responseBody: String)(implicit mat: Materializer, formats: Formats): A = {
    Try(read[A](responseBody)) match {
      case Success(parsed) => parsed
      case Failure(ex) =>
        logger.error(s"could not parse response: $ex \n$responseBody")
        throw ex
    }
  }

  def execute[A](request: HttpRequest)(implicit formats: Formats): Future[Either[String, A]] =
    for {
      resp: HttpResponse <- http.singleRequest(request, SecurityContext)
      str: String <- consumeEntity(resp.entity)
    } yield {
      resp.status match {
        case code: StatusCode if code.isSuccess() => Right(parseJson(str))
        case _ =>
          logger.warn(s"Response ${resp.status.value} ${request.uri} $str")
          Left(str)
      }
    }

  private def consumeEntity(entity: HttpEntity)(implicit mat: Materializer): Future[String] =
    entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String)

}