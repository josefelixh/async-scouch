package org.josefelixh

import org.josefelixh.couch._
import org.josefelixh.couch.CouchDocument._
import play.api.libs.json._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



object Asyncscouch extends App {
  println("Hello, async-scouch")

  case class Role(name: String, permissions: Int)
  case class Profile(id: String, level: Int, roles: Seq[Role])

  val role = Role("admin", 777)
  val profile = Profile("badger", 0, Vector(role))

  implicit val couch = Couch("heroku")
  implicit val roleFormat = Json.format[Role]
  implicit val profileFormat = Json.format[Profile]

  val future = for {
    r1 <- create(profile)
    docId = Id[Profile](r1.id.get)
    g1 <- (docId.retrieve)
    u1 <- r1 update (current => current.copy(level = 1))
    g2 <- (docId.retrieve)
    d1 <- u1.delete
  } yield {
    println(s"CREATED : $r1")
    println(s"RETREIVED : $g1")
    println(s"UPDATED : $u1")
    println(s"RETREIVED : $g2")
    println(s"DELETED : ${d1.json}")
    System.exit(0)
  }

  val future2 = {
    import CouchDocument._
    create(profile)
  }
  future.onFailure {
    case t => println("An error has occured: " + t.getMessage)
  }

  import language.postfixOps
  Await.result(future, 30 seconds)

  def DeleteAll =
    couch.documents map { response =>
      println(s"Documents ${response.json}")
      val ids = (response.json \\ "id") map { _.validate[String].asOpt }
      for (id <- ids) yield id match {
        case x @ Some(_) => {
          println("Trying to delete id: " + id.get)
          for {
            doc <- CouchDocument[JsValue](x, None, Some(JsNull)).retrieve
            deleteResponse <- doc.delete
          } yield concurrent.future(println(s"Status: ${deleteResponse.statusText} Body: ${deleteResponse.body}"))
        }

        case None => concurrent.future(println("No Id found"))
    }}

//  Await.result(deleteAll, 30 seconds) map {f => Await.ready(f, 20 seconds)}

  System.exit(0)
}

