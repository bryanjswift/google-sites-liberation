/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sites.liberation.parsers;

import static org.junit.Assert.*;

import com.google.gdata.data.TextConstruct;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author bsimon@google.com (Benjamin Simon)
 */
public class SummaryParserImplTest {

  private SummaryParser parser;
   
  @Before
  public void before() {
    parser = new SummaryParserImpl();  
  }
  
  @Test
  public void testNormalCase() {
    String html = "<span class=\"summary\">A story about dogs.</span>";
    Element element = getElement(html);
    TextConstruct summary = parser.parseSummary(element);
    assertEquals("A story about dogs.", summary.getPlainText());   
  }
  
  @Test
  public void testComplicatedStructure() {
    String html = "<div class=\"summary\">" +
                    "<a href=\"website.com\">Link</a> " +
                    "<table><tr><td><b>yeah!</b></td></tr></table>" +
                  "</div>";
    Element element = getElement(html);
    TextConstruct summary = parser.parseSummary(element);
    assertEquals("Link yeah!", summary.getPlainText());
  }
  
  private Element getElement(String html) {
    ByteArrayInputStream stream = new ByteArrayInputStream(html.getBytes());
    Document document = null;
    try {
      document = DocumentBuilderFactory.newInstance()
          .newDocumentBuilder().parse(stream);
    } catch (SAXException e) {
      fail("Invalid html!");
    } catch (IOException e) {
      fail("Invalid html!");
    } catch (ParserConfigurationException e) {
      fail("Invalid html!");
    }
    return document.getDocumentElement();
  }
}
