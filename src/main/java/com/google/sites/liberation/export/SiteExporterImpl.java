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

package com.google.sites.liberation.export;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.sites.liberation.util.EntryType.ATTACHMENT;
import static com.google.sites.liberation.util.EntryType.getType;
import static com.google.sites.liberation.util.EntryType.isPage;
import static com.google.sites.liberation.util.EntryUtils.getParentId;

import com.google.common.collect.Sets;
import com.google.gdata.client.sites.SitesService;
import com.google.gdata.data.sites.AttachmentEntry;
import com.google.gdata.data.sites.BaseContentEntry;
import com.google.gdata.data.sites.BasePageEntry;
import com.google.gdata.util.common.base.Nullable;
import com.google.inject.Inject;
import com.google.sites.liberation.util.ProgressListener;
import com.google.sites.liberation.util.UrlUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements {@link SiteExporter} to export an entire Site
 * to a given root folder.
 *
 * @author bsimon@google.com (Benjamin Simon)
 */
final class SiteExporterImpl implements SiteExporter {

  private static final Logger LOGGER = Logger.getLogger(
      SiteExporterImpl.class.getCanonicalName());

  private final AbsoluteLinkConverter linkConverter;
  private final AppendableFactory appendableFactory;
  private final AttachmentDownloader attachmentDownloader;
  private final EntryStoreFactory entryStoreFactory;
  private final FeedProvider feedProvider;
  private final PageExporter pageExporter;
  private final RevisionsExporter revisionsExporter;

  /**
   * Creates a new SiteExporter with the given dependencies.
   */
  @Inject
  SiteExporterImpl(AbsoluteLinkConverter linkConverter,
      AppendableFactory appendableFactory,
      AttachmentDownloader attachmentDownloader,
      EntryStoreFactory entryStoreFactory,
      FeedProvider feedProvider,
      PageExporter pageExporter,
      RevisionsExporter revisionsExporter) {
    this.linkConverter = checkNotNull(linkConverter);
    this.appendableFactory = checkNotNull(appendableFactory);
    this.attachmentDownloader = checkNotNull(attachmentDownloader);
    this.entryStoreFactory = checkNotNull(entryStoreFactory);
    this.feedProvider = checkNotNull(feedProvider);
    this.pageExporter = checkNotNull(pageExporter);
    this.revisionsExporter = checkNotNull(revisionsExporter);
  }

  private class ProgressUpdater {
    private final int totalEntries;
    private final ProgressListener progressListener;
    private int current = 0;
    public ProgressUpdater(int totalEntries, ProgressListener progressListener) {
      this.totalEntries = totalEntries;
      this.progressListener = progressListener;
    }
    public synchronized void increment() {
      current = current + 1;
      if (current == totalEntries) {
        progressListener.setStatus("Export Finished.");
      }
      progressListener.setProgress(((double) current) / totalEntries);
    }
  }

  @Override
  public void exportSite(final String host, @Nullable final String domain, final String webspace,
      final boolean exportRevisions, final SitesService sitesService, final File rootDirectory,
      final ProgressListener progressListener) {
    checkNotNull(host, "host");
    checkNotNull(webspace, "webspace");
    checkNotNull(sitesService, "sitesService");
    checkNotNull(rootDirectory, "rootDirectory");
    checkNotNull(progressListener, "progressListener");
    Set<BasePageEntry<?>> pages = Sets.newHashSet();
    Set<AttachmentEntry> attachments = Sets.newHashSet();
    final EntryStore entryStore = entryStoreFactory.newEntryStore();
    final URL feedUrl = UrlUtils.getFeedUrl(host, domain, webspace);
    final URL siteUrl = UrlUtils.getSiteUrl(host, domain, webspace);

    progressListener.setStatus("Retrieving site data (this may take a few minutes).");
    Iterable<BaseContentEntry<?>> entries =
        feedProvider.getEntries(feedUrl, sitesService);
    int num = 1;
    for (BaseContentEntry<?> entry : entries) {
      if (entry != null) {
        if (num % 20 == 0) {
          progressListener.setStatus("Retrieved " + num + " entries.");
        }
        entryStore.addEntry(entry);
        if (isPage(entry)) {
          pages.add((BasePageEntry<?>) entry);
        } else if (getType(entry) == ATTACHMENT) {
          // TODO(gk5885): remove extra cast for
          // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6302214
          attachments.add((AttachmentEntry) (BaseContentEntry) entry);
        }
        num++;
      } else {
        LOGGER.log(Level.WARNING, "Error parsing entries!");
      }
    }
    final int totalEntries = pages.size() + attachments.size();
    final ProgressUpdater updater = new ProgressUpdater(totalEntries,progressListener);
    ExecutorService exec = Executors.newFixedThreadPool(4);

    class PageRunnable implements Runnable {
      final BasePageEntry<?> page;
      public PageRunnable(BasePageEntry<?> page) {
        this.page = page;
      }
      public void run() {
        progressListener.setStatus("Exporting page: " + page.getTitle().getPlainText() + '.');
        XhtmlCleanup.apply(page);
        linkConverter.convertLinks(page, entryStore, siteUrl, false);
        File relativePath = getPath(page, entryStore);
        if (relativePath != null) {
          File directory = new File(rootDirectory, relativePath.getPath());
          directory.mkdirs();
          exportPage(page, directory, entryStore, exportRevisions);
          if (exportRevisions) {
            revisionsExporter.exportRevisions(page, entryStore, directory,
                sitesService, siteUrl);
          }
        }
        progressListener.setStatus("Finished page: " + page.getTitle().getPlainText() + '.');
        updater.increment();
      }
    }
    class DownloadRunnable implements Runnable {
      final AttachmentEntry attachment;
      public DownloadRunnable(AttachmentEntry attachment) {
        this.attachment = attachment;
      }
      public void run() {
        progressListener.setStatus("Downloading attachment: " + attachment.getTitle().getPlainText() + '.');
        downloadAttachment(attachment, rootDirectory, entryStore, sitesService);
        updater.increment();
      }
    }

    if (totalEntries > 0) {
      progressListener.setStatus("Exporting " + totalEntries + " total pages");
      for (BasePageEntry<?> page : pages) {
        exec.execute(new PageRunnable(page));
      }
      for (AttachmentEntry attachment : attachments) {
        exec.execute(new DownloadRunnable(attachment));
      }
    } else {
      progressListener.setStatus("No data returned. You may have provided "
          + "invalid Site information or credentials.");
    }
  }


  private void exportPage(BasePageEntry<?> page, File directory,
      EntryStore entryStore, boolean revisionsExported) {
    File file = new File(directory, "index.html");
    Appendable out = null;
    try {
      out = appendableFactory.getAppendable(file);
      pageExporter.exportPage(page, entryStore, out, revisionsExported);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed writing to file: " + file.getPath(), e);
    } finally {
      if (out instanceof Closeable) {
        try {
          ((Closeable) out).close();
        } catch (IOException e) {
          LOGGER.log(Level.SEVERE, "Failed closing file: " + file.getPath(), e);
        }
      }
    }
  }

  private void downloadAttachment(AttachmentEntry attachment,
      File rootDirectory, EntryStore entryStore, SitesService sitesService) {
    BasePageEntry<?> parent = entryStore.getParent(attachment.getId());
    if (parent != null) {
      File relativePath = getPath(parent, entryStore);
      if (relativePath != null) {
        File folder = new File(rootDirectory, relativePath.getPath());
        folder.mkdirs();
        File file = new File(folder, attachment.getTitle().getPlainText());
        attachmentDownloader.download(attachment, file, sitesService);
      }
    }
  }

  /**
   * Returns the site-relative folder path corresponding to the given page, or
   * {@code null} if any of the page's ancestors are missing.
   */
  private File getPath(BasePageEntry<?> entry, EntryStore entryStore) {
    String parentId = getParentId(entry);
    if (parentId == null) {
      return new File(entry.getPageName().getValue());
    }
    BasePageEntry<?> parent = (BasePageEntry<?>) entryStore.getEntry(parentId);
    if (parent == null) {
      return null;
    }
    return new File(getPath(parent, entryStore), entry.getPageName().getValue());
  }
}
