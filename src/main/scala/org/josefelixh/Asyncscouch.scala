package org.josefelixh

import org.josefelixh.couch._
import org.josefelixh.couch.CouchDocument._
import play.api.libs.json._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent._
import org.josefelixh.couch._
import scala.Predef._
import org.josefelixh.couch.CouchConfig
import scala.Some
import scala.util.{Success, Failure}


object Asyncscouch extends App {
  println("Hello, async-scouch")

  case class Role(name: String, permissions: Int)
  case class Profile(id: String, level: Int, roles: Seq[Role])

  val role = Role("admin", 777)
  val profile = Profile("badger", 0, Vector(role))

  val credentials = Some(("username", "password"))
  val couchConfig: CouchConfig = CouchConfig("https://username.cloudant.com", "dbname", credentials)

  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val couch = Couch(couchConfig)
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

  future.onComplete {
    case Success(x) => println("Success!!!")
    case Failure(t) => println("An error has occured: " + t.getMessage)
  }


  import language.postfixOps
  Await.result(future, 10 seconds)
  sys.exit(0)
}

