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

package com.google.sites.liberation.renderers;

import com.google.gdata.data.sites.BaseContentEntry;
import com.google.sites.liberation.util.XmlElement;

import java.util.List;

/**
 * Renders the file cabinet in a file cabinet page.
 * 
 * @author bsimon@google.com (Benjamin Simon)
 */
final class FileCabinetRendererImpl implements FileCabinetRenderer {

  @Override
  public XmlElement renderFileCabinet( 
      List<BaseContentEntry<?>> attachments) {
    XmlElement table = new XmlElement("table");
    for (BaseContentEntry<?> attachment : attachments) {
      XmlElement row = RendererUtils.getEntryElement(attachment, "tr");
      XmlElement link = RendererUtils.getOutOfLineContentElement(attachment);
      row.addElement(new XmlElement("td").addElement(link));
      XmlElement summary = RendererUtils.getSummaryElement(attachment);
      row.addElement(new XmlElement("td").addElement(summary));
      XmlElement updated = RendererUtils.getUpdatedElement(attachment);
      row.addElement(new XmlElement("td").addElement(updated));
      XmlElement author = RendererUtils.getAuthorElement(attachment);
      row.addElement(new XmlElement("td").addElement(author));
      XmlElement revision = RendererUtils.getRevisionElement(attachment);
      row.addElement(new XmlElement("td").addText("(Version ")
          .addElement(revision).addText(")"));
      table.addElement(row);
    }
    return table;
  }
}
