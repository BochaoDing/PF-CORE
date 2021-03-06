/*
 * Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id$
 */
package de.dal33t.powerfolder.message;

import com.google.protobuf.AbstractMessage;
import de.dal33t.powerfolder.d2d.D2DObject;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.protocol.DownloadRequestProto;
import de.dal33t.powerfolder.protocol.FileInfoProto;

/**
 * Request to start download a file
 *
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.3 $
 */
public class RequestDownload extends Message
  implements D2DObject
{
    private static final long serialVersionUID = 100L;

    public FileInfo file;
    public long startOffset;

    public RequestDownload() {
        // Serialisation constructor
    }

    /**
     * Start a complete new download
     *
     * @param file
     */
    public RequestDownload(FileInfo file) {
        this(file, 0);
    }

    /**
     * Requests file download, starting at offset
     *
     * @param file
     * @param startOffset
     */
    public RequestDownload(FileInfo file, long startOffset) {
        super();
        this.file = file;
        this.startOffset = startOffset;
    }

    @Override
    public String toString() {
        return "Request to download: " + file.toDetailString()
            + ", starting at " + startOffset;
    }

    /** initFromD2DMessage
     * Init from D2D message
     * @author Christoph Kappel <kappel@powerfolder.com>
     * @param  mesg  Message to use data from
     **/

    @Override
    public void
    initFromD2D(AbstractMessage mesg)
    {
      if(mesg instanceof DownloadRequestProto.DownloadRequest)
        {
          DownloadRequestProto.DownloadRequest proto =
            (DownloadRequestProto.DownloadRequest)mesg;

          this.file        = new FileInfo(proto.getFileInfo());
          this.startOffset = proto.getStartOffset();
        }
    }

    /** toD2D
     * Convert to D2D message
     * @author Christoph Kappel <kappel@powerfolder.com>
     * @return Converted D2D message
     **/

    @Override
    public AbstractMessage
    toD2D()
    {
      DownloadRequestProto.DownloadRequest.Builder builder =
        DownloadRequestProto.DownloadRequest.newBuilder();

      // Translate old message name to new name defined in protocol file
      builder.setClazzName("DownloadRequest");
      builder.setFileInfo((FileInfoProto.FileInfo)this.file.toD2D());

      return builder.build();
    }
}