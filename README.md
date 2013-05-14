# Async-scouch
Async-scouch is a BigCouch/Cloudant client library. It based on top of netty, ning asynchronous http client and the super cool play-json libraries based on jerkson/jackson. So far is targets scala, but the intention is to make it work for java as well.


### Usage (Scala)

##### Basic CRUD operations

First you need some stuff to persist.

    case class Role(name: String, permissions: Int)
    case class Profile(id: String, level: Int, roles: Seq[Role])
    
    val role = Role("admin", 777)
    val profile = Profile("badger", 0, Vector(role))
    
    
Then configure the client

    val credentials = Some(("username", "password"))
    val couchConfig = CouchConfig("https://couchinstance.com:port", "dbname", credentials)

    implicit val executionContext = ExecutionContext.Implicits.global
    implicit val couch = Couch(couchConfig)
    
Provide a way to serialise/deserialise your objects to json

    implicit val roleFormat = Json.format[Role]
    implicit val profileFormat = Json.format[Profile]
    
Then you can just use CouchDocument to do CRUD operations with your objects. 

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
      case Success(x) => println("Easy!!!")
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    
    
In order to see some results it is needed to wait for the future, but this is something you might not want to do in your application.

    Await.result(future, 10 seconds)

##Licence

  This software is licensed under the Apache 2 license, quoted below.

  Copyright 2013 josefelixh (http://github.com/josefelixh).
    
  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
