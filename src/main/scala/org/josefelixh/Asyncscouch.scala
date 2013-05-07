package org.josefelixh

import org.josefelixh.couch._
import org.josefelixh.couch.CouchDocument._
import play.api.libs.json._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


case class Role(val name: String, val permissions: Int)
case class Profile(val id: String, level: Int, roles: Seq[Role])

object Asyncscouch extends App {
  import scala.language.postfixOps

  println("Hello, async-scouch")


  val role = Role("admin", 777)
  val profile = Profile("badger", 0, Vector(role))

  implicit val couch = Couch("heroku")
  implicit val roleFormat = Json.format[Role]
  implicit val profileFormat = Json.format[Profile]

  def deleteAll =
    couch.documents map { response =>
      println(s"Documents ${response.json}")
      (response.json \\ "id") map { _.validate[String].asOpt } map {
        case id @ Some(_) => {
          println("Trying to delete id: " + id.get)
          for {
            doc <- CouchDocument[JsValue](id, None, JsNull).retrieve
            deleteResponse <- doc.delete
          } yield println(s"Status: ${deleteResponse.statusText} Body: ${deleteResponse.body}")
        }

        case None => println("No Id found")
    }}

  def createSome = {
    (for (id <- 1 to 10) yield {
      CouchDocument(Some("0APROFILE_"+id), None, profile) create
    })
  }

  val delete = deleteAll
  Thread.sleep(20000)
  val create = createSome

  (delete +: create) map { f => Await.ready(f, 10 seconds)}
//  Thread.sleep(20000)
  System.exit(0)
}

