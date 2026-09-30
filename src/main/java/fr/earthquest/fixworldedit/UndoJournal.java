package fr.earthquest.fixworldedit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class UndoJournal {
    private static final int MAGIC = 0x51544A31;

    private final File file;
    private DataOutputStream out;
    private int records;

    public UndoJournal(File file) {
        this.file = file;
    }

    public boolean exists() {
        return file.isFile();
    }

    public int records() {
        return records;
    }

    public void open(String worldName) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Dossier d'annulation impossible a creer : " + parent);
        }
        out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))));
        out.writeInt(MAGIC);
        out.writeUTF(worldName);
        records = 0;
    }

    public void record(int x, int y, int z, int id, int data) throws IOException {
        if (out == null) {
            return;
        }
        out.writeInt(x);
        out.writeShort(y);
        out.writeInt(z);
        out.writeShort(id);
        out.writeByte(data);
        records++;
    }

    public void close() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
        }
        out = null;
    }

    public void delete() {
        close();
        file.delete();
    }

    public Reader read() throws IOException {
        return new Reader(file);
    }

    public static final class Reader {
        private final DataInputStream in;
        private final String worldName;

        public int x;
        public int y;
        public int z;
        public int id;
        public int data;

        Reader(File file) throws IOException {
            this.in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))));
            if (in.readInt() != MAGIC) {
                in.close();
                throw new IOException("Journal d'annulation illisible : " + file.getName());
            }
            this.worldName = in.readUTF();
        }

        public String worldName() {
            return worldName;
        }

        public boolean next() throws IOException {
            try {
                x = in.readInt();
                y = in.readShort();
                z = in.readInt();
                id = in.readShort() & 0xFFFF;
                data = in.readByte() & 0xFF;
                return true;
            } catch (EOFException end) {
                return false;
            }
        }

        public void close() {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }
}
