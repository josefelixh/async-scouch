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
    createdNoId <- CouchDocument(profile).create
    created <- CouchDocument("PROFILE_ID", profile).create
    docId = Id[Profile](createdNoId.id.get)
    retreived <- docId.retrieve
    updated <- createdNoId.update(current => current.copy(level = 1))
    updateRetreived <- docId.retrieve
    deleteResponse <- updated.delete
    deleteResponse2 <- created.delete
  } yield {
    println(s"CREATED : $createdNoId")
    println(s"CREATED : $created")
    println(s"RETREIVED : $retreived")
    println(s"UPDATED : $updated")
    println(s"RETREIVED : $updateRetreived")
    println(s"DELETED : ${deleteResponse.json}")
    println(s"DELETED : ${deleteResponse2.json}")
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

//  Await.result(DeleteAll, 30 seconds) map {f => Await.ready(f, 20 seconds)}

  System.exit(0)
}

