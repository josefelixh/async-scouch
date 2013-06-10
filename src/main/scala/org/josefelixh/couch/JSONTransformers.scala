package org.josefelixh.couch

import play.api.libs.json._
import org.josefelixh.libs.http.Response

trait JSONTransformers[T] {
  this: CouchDocument[T] =>
  type Transformer = Reads[JsObject]

  private[couch] val PruneOk = (__ \ 'ok).json.prune
  private[couch] val Prune_id = (__ \ '_id).json.prune
  private[couch] val Prune_rev = (__ \ '_rev).json.prune
  private[couch] val NooP = __.read[JsObject]

  private[couch] val AddCouchId = __.json.update {
    NooP map { o => Json.obj("_id" -> id.get) ++ o }
  }

  private[couch] val AddCouchIdToJson = {
    id match {
      case Some(couchId) => AddCouchId
      case None => NooP
    }
  }

  private[couch] def transformResponse(transformer: Transformer)(implicit response: Response) = response.json.transform(transformer)
}
