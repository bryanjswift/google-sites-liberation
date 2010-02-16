package com.google.sites.liberation.export

import junit.framework.TestCase
import org.junit.Assert._

class XhtmlCleanupTest extends TestCase {
	override def setUp { }
	override def tearDown { }
	def testFontBlocksGone = {
		val div =
			<div>
				<font><a href="test.html">This is some text</a></font>
				<p><font>More text</font></p>
			</div>
		val result = XhtmlCleanup.clean(div)
		assertEquals(0,(result \\ "font").length)
		assertEquals("This is some text",(result \ "a").text)
		assertEquals("More text",(result \ "p").text)
	}
	def testEmptyLinksGone = {
		val div =
			<div>
				<font><a href="test.html"></a>This is some text</font>
				<p><a href="test.html" />More text</p>
			</div>
		val result = XhtmlCleanup.clean(div)
		assertEquals(0,(result \\ "a").length)
		assertEquals("This is some text",(result \ "font").text)
		assertEquals("More text",(result \ "p").text)
	}
}
