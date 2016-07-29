package brut.directory;

import brut.common.BrutException;
import brut.util.OS;

import java.io.*;

public class IFile {

    private static int count = 0;
    private File file;

    public IFile(String path) {
        file = new File(path);
    }

    public IFile(String parentPath, String child) {
        file = new File(parentPath, child);
    }

    public IFile(IFile parent, String child) {
        file = new File(parent.file, child);
    }

    public boolean canRead() { return file.canRead(); }
    public boolean isDirectory() { return file.isDirectory(); }
    public boolean isFile() { return file.isFile(); }

    public IFile getParentFile() {
        return new IFile(file.getParentFile().getAbsolutePath());
    }

    public Directory getDirectory() throws DirectoryException {
        if (isDirectory()) {
            return new FileDirectory(this);
        } else {
            return new ZipRODirectory(file);
        }
    }

    public String getPath() {
        return file.getPath();
    }
    public String getAbsolutePath() {
        return file.getAbsolutePath();
    }
    public String getName() {
        return file.getName();
    }

    public boolean exists() {
        return false;
    }

    public void mkdir() {
        file.mkdir();
    }

    public void rmdir() {
        try {
            OS.rmdir(file);
        } catch (BrutException e) {
            e.printStackTrace();
        }
    }

    public void mkdirs() {
        file.mkdirs();
    }

    public IFile[] listFiles() {
        File[] list = file.listFiles();
        IFile[] files = new IFile[list.length];

        for(int i = 0 ; i < list.length; ++i){
            files[i] = new IFile(list[i].getAbsolutePath());
        }

        return files;
    }

    public OutputStream getOutputStream() throws FileNotFoundException {
        count += 1;
        System.out.printf("create file #%d: %s\n", count, getAbsolutePath());

        return new FileOutputStream(file);
    }

    public InputStream getInputStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

    public Directory toDirectory() throws DirectoryException {
        //System.out.println("IFile.toDirectory: " + file.toString());
        return new FileDirectory(this);
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
