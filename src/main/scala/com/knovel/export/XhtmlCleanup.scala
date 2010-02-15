package com.knovel.export

import com.google.gdata.data.sites.BasePageEntry
import com.google.sites.liberation.util.EntryUtils
import scala.xml.{Elem,Node,NodeSeq,XML}
import scala.xml.transform.{RewriteRule,RuleTransformer}

object XhtmlCleanup {
	def process(entry:BasePageEntry[_]) = {
		val content = EntryUtils.getXhtmlContent(entry)
		val block = XML.loadString(content)
		val result = cleanEmptyLinks(cleanFontTags(block))
		EntryUtils.setContent(entry, result.toString)
		entry
	}
	private[export] object cleanFontTags extends RuleTransformer(new RewriteRule {
		override def transform(n:Node) =
			n match {
				case Elem(prefix,"font",attributes,scope,children) => children
				case other => other
			}
	})
	private[export] object cleanEmptyLinks extends RuleTransformer(new RewriteRule {
		override def transform(n:Node) =
			n match {
				case a @ Elem(_,"a",_,_,_*) => {
					if (a.length == 1) Seq[Node]()
					else a
				}
				case other => other
			}
	})
}
