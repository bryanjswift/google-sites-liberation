package com.google.sites.liberation.export

import com.google.gdata.data.sites.BasePageEntry
import com.google.sites.liberation.util.EntryUtils
import scala.xml.{Elem,Node,XML}
import scala.xml.transform.{RewriteRule,RuleTransformer}

object XhtmlCleanup {
  def apply(entry:BasePageEntry[_]) =
    try {
      val content = EntryUtils.getXhtmlContent(entry)
      val block = XML.loadString(content)
      val result = clean(block)
      EntryUtils.setContent(entry, result.toString)
      entry
    } catch {
      case e => entry
    }
  private[export] object clean extends RuleTransformer(new RewriteRule {
    override def transform(n:Node) =
      n match {
        //case Elem(prefix,"font",attributes,scope,children) => children
        case a @ Elem(_,"a",_,_,_*) => {
          if (a.length == 1 && a.text == "") Seq[Node]()
          else a
        }
        case _ => n
      }
  })
}
