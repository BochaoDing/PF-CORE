package de.dal33t.powerfolder.test.message;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

/**
 * File information of a local or remote file
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.33 $
 */
public class FileInfoExternalizable implements Externalizable {

    /** The filename (including the path from the base of the folder) */
    public String fileName;

    /** The size of the file */
    public Long size;

    /** modified info */
    public String modifiedBy;
    /** modified in folder on date */
    public Date lastModifiedDate;

    /** Version number of this file */
    public int version;

    /** the deleted flag */
    public boolean deleted;

    /** the folder */
    public String folderInfo;

    // Serialization optimization *********************************************

    public void readExternal(ObjectInput in) throws IOException,
        ClassNotFoundException
    {
        fileName = in.readUTF();
        size = Long.valueOf(in.readLong());
        modifiedBy = in.readUTF();
        lastModifiedDate = (Date) in.readObject();
        version = in.readInt();
        deleted = in.readBoolean();
        folderInfo = in.readUTF();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileName);
        out.writeLong(size.longValue());
        out.writeUTF(modifiedBy);
        out.writeObject(lastModifiedDate);
        out.writeInt(version);
        out.writeBoolean(deleted);
        out.writeUTF(folderInfo);
    }
}