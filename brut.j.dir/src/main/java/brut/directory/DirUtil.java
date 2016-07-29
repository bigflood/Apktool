/**
 *  Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package brut.directory;

import brut.common.BrutException;
import brut.util.BrutIO;
import brut.util.OS;
import java.io.*;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class DirUtil {
    public static void copyToDir(Directory in, Directory out)
            throws DirectoryException {
        for (String fileName : in.getFiles(true)) {
            copyToDir(in, out, fileName);
        }
    }

    public static void copyToDir(Directory in, Directory out,
            String[] fileNames) throws DirectoryException {
        for (int i = 0; i < fileNames.length; i++) {
            copyToDir(in, out, fileNames[i]);
        }
    }

    public static void copyToDir(Directory in, Directory out, String fileName)
            throws DirectoryException {
        try {
            if (in.containsDir(fileName)) {
                // TODO: remove before copying
                in.getDir(fileName).copyToDir(out.createDir(fileName));
            } else {
                BrutIO.copyAndClose(in.getFileInput(fileName),
                    out.getFileOutput(fileName));
            }
        } catch (IOException ex) {
            throw new DirectoryException(
                "Error copying file: " + fileName, ex);
        }
    }

    public static void copyToDir(Directory in, IFile out)
            throws DirectoryException {
        for (String fileName : in.getFiles(true)) {
            copyToDir(in, out, fileName);
        }
    }

    public static void copyToDir(Directory in, IFile out, String[] fileNames)
            throws DirectoryException {
        for (int i = 0; i < fileNames.length; i++) {
            copyToDir(in, out, fileNames[i]);
        }
    }

    public static void copyToDir(Directory in, IFile out, String fileName)
            throws DirectoryException {
        try {
            if (in.containsDir(fileName)) {
                new IFile(out, fileName).rmdir();
                in.getDir(fileName).copyToDir(new IFile(out, fileName));
            } else {
                if (fileName.equals("res") && !in.containsFile(fileName)) {
                    return;
                }
                IFile outFile = new IFile(out, fileName);
                outFile.getParentFile().mkdirs();
                BrutIO.copyAndClose(in.getFileInput(fileName),
                    outFile.getOutputStream());
            }
        } catch (IOException ex) {
            throw new DirectoryException(
                "Error copying file: " + fileName, ex);
        } catch (BrutException ex) {
            throw new DirectoryException(
                "Error copying file: " + fileName, ex);
        }
    }
}
