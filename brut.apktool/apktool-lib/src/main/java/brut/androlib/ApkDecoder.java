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

package brut.androlib;

import brut.androlib.err.InFileNotFoundException;
import brut.androlib.err.OutDirExistsException;
import brut.androlib.err.UndefinedResObject;
import brut.androlib.meta.MetaInfo;
import brut.androlib.meta.PackageInfo;
import brut.androlib.meta.UsesFramework;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;
import brut.directory.DirectoryException;
import brut.directory.IFile;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class ApkDecoder {
    public ApkDecoder() {
        this(new Androlib());
    }

    public ApkDecoder(Androlib androlib) {
        mAndrolib = androlib;
    }

    public void setApkFile(IFile apkFile) {
        mApkFile = apkFile;
        mResTable = null;
    }

    public void setOutDir(IFile outDir) throws AndrolibException {
        mOutDir = outDir;
    }

    public void setApi(int api) {
        mApi = api;
    }

    public void decode() throws AndrolibException, IOException, DirectoryException {
        IFile outDir = getOutDir();
        AndrolibResources.sKeepBroken = mKeepBrokenResources;

        if (!mForceDelete && outDir.exists()) {
            throw new OutDirExistsException();
        }

        if (!mApkFile.isFile() || !mApkFile.canRead()) {
            throw new InFileNotFoundException();
        }

        outDir.rmdir();
        outDir.mkdirs();

        if (hasResources()) {
            switch (mDecodeResources) {
                case DECODE_RESOURCES_NONE:
                    mAndrolib.decodeResourcesRaw(mApkFile, outDir);
                    break;
                case DECODE_RESOURCES_FULL:
                    setTargetSdkVersion();
                    setAnalysisMode(mAnalysisMode, true);

                    if (hasManifest()) {
                        mAndrolib.decodeManifestWithResources(mApkFile, outDir, getResTable());
                    }
                    mAndrolib.decodeResourcesFull(mApkFile, outDir, getResTable());
                    break;
            }
        } else {
            // if there's no resources.asrc, decode the manifest without looking
            // up attribute references
            if (hasManifest()) {
                switch (mDecodeResources) {
                    case DECODE_RESOURCES_NONE:
                        mAndrolib.decodeManifestRaw(mApkFile, outDir);
                        break;
                    case DECODE_RESOURCES_FULL:
                        mAndrolib.decodeManifestFull(mApkFile, outDir,
                                getResTable());
                        break;
                }
            }
        }

        if (hasSources()) {
            mAndrolib.decodeSourcesRaw(mApkFile, outDir, "classes.dex");
            mAndrolib.decodeSourcesSmali(mApkFile, outDir, "classes.dex", mBakDeb, mApi);
        }

        if (hasMultipleSources()) {
            // foreach unknown dex file in root, lets disassemble it
            Set<String> files = mApkFile.getDirectory().getFiles(true);
            for (String file : files) {
                if (file.endsWith(".dex")) {
                    if (! file.equalsIgnoreCase("classes.dex")) {
                        mAndrolib.decodeSourcesRaw(mApkFile, outDir, file);
                        mAndrolib.decodeSourcesSmali(mApkFile, outDir, file, mBakDeb, mApi);
                    }
                }
            }
        }

        mAndrolib.decodeRawFiles(mApkFile, outDir);
        mAndrolib.decodeUnknownFiles(mApkFile, outDir, mResTable);
        mUncompressedFiles = new ArrayList<String>();
        mAndrolib.recordUncompressedFiles(mApkFile, mUncompressedFiles);
        //mAndrolib.writeOriginalFiles(mApkFile, outDir);
        writeMetaFile();
    }

    public void setDecodeResources(short mode) throws AndrolibException {
        if (mode != DECODE_RESOURCES_NONE && mode != DECODE_RESOURCES_FULL) {
            throw new AndrolibException("Invalid decode resources mode");
        }
        mDecodeResources = mode;
    }

    public void setAnalysisMode(boolean mode, boolean pass) throws AndrolibException{
        mAnalysisMode = mode;

        // only set mResTable, once it exists
        if (pass) {
            if (mResTable == null) {
                mResTable = getResTable();
            }
            mResTable.setAnalysisMode(mode);
        }
    }

    public void setTargetSdkVersion() throws AndrolibException, IOException {
        if (mResTable == null) {
            mResTable = mAndrolib.getResTable(mApkFile);
        }

        Map<String, String> sdkInfo = mResTable.getSdkInfo();
        if (sdkInfo.get("targetSdkVersion") != null) {
            mApi = Integer.parseInt(sdkInfo.get("targetSdkVersion"));
        }
    }

    public void setBaksmaliDebugMode(boolean bakdeb) {
        mBakDeb = bakdeb;
    }

    public void setForceDelete(boolean forceDelete) {
        mForceDelete = forceDelete;
    }

    /*
    public void setFrameworkTag(String tag) throws AndrolibException {
        mAndrolib.apkOptions.frameworkTag = tag;
    }
    */

    public void setKeepBrokenResources(boolean keepBrokenResources) {
        mKeepBrokenResources = keepBrokenResources;
    }

    public ResTable getResTable() throws AndrolibException {
        if (mResTable == null) {
            boolean hasResources = hasResources();
            boolean hasManifest = hasManifest();
            if (! (hasManifest || hasResources)) {
                throw new AndrolibException(
                        "Apk doesn't contain either AndroidManifest.xml file or resources.arsc file");
            }
            mResTable = mAndrolib.getResTable(mApkFile, hasResources);
        }
        return mResTable;
    }

    public boolean hasSources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("classes.dex");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public boolean hasMultipleSources() throws AndrolibException {
        try {
            Set<String> files = mApkFile.getDirectory().getFiles(false);
            for (String file : files) {
                if (file.endsWith(".dex")) {
                    if (! file.equalsIgnoreCase("classes.dex")) {
                        return true;
                    }
                }
            }

            return false;
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public boolean hasManifest() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("AndroidManifest.xml");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public boolean hasResources() throws AndrolibException {
        try {
            return mApkFile.getDirectory().containsFile("resources.arsc");
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public final static short DECODE_RESOURCES_NONE = 0x0100;
    public final static short DECODE_RESOURCES_FULL = 0x0101;

    private IFile getOutDir() throws AndrolibException {
        if (mOutDir == null) {
            throw new AndrolibException("Out dir not set");
        }
        return mOutDir;
    }

    private void writeMetaFile() throws AndrolibException {
        MetaInfo meta = new MetaInfo();
        //meta.version = Androlib.getVersion();
        meta.apkFileName = mApkFile.getName();

        if (mDecodeResources != DECODE_RESOURCES_NONE && (hasManifest() || hasResources())) {
            //meta.isFrameworkApk = mAndrolib.isFrameworkApk(getResTable());
            //putUsesFramework(meta);
            putSdkInfo(meta);
            putPackageInfo(meta);
            //putVersionInfo(meta);
            putSharedLibraryInfo(meta);
        }
        putUnknownInfo(meta);
        putFileCompressionInfo(meta);

        mAndrolib.writeMetaFile(mOutDir, meta);
    }

    private void putSdkInfo(MetaInfo meta) throws AndrolibException {
        Map<String, String> info = getResTable().getSdkInfo();
        if (info.size() > 0) {
            meta.sdkInfo = info;
        }
    }

    private void putPackageInfo(MetaInfo meta) throws AndrolibException {
        String renamed = getResTable().getPackageRenamed();
        String original = getResTable().getPackageOriginal();

        int id = getResTable().getPackageId();
        try {
            id = getResTable().getPackage(renamed).getId();
        } catch (UndefinedResObject ignored) {}

        if (Strings.isNullOrEmpty(original)) {
            return;
        }

        meta.packageInfo = new PackageInfo();

        // only put rename-manifest-package into apktool.yml, if the change will be required
        if (!renamed.equalsIgnoreCase(original)) {
            meta.packageInfo.renameManifestPackage = renamed;
        }
        meta.packageInfo.forcedPackageId = String.valueOf(id);
    }

    private void putUnknownInfo(MetaInfo meta) throws AndrolibException {
        meta.unknownFiles = mAndrolib.mResUnknownFiles.getUnknownFiles();
    }

    private void putFileCompressionInfo(MetaInfo meta) throws AndrolibException {
        if (!mUncompressedFiles.isEmpty()) {
            meta.doNotCompress = mUncompressedFiles;
        }
    }

    private void putSharedLibraryInfo(MetaInfo meta) throws AndrolibException {
        meta.sharedLibrary = mResTable.getSharedLibrary();
    }

    private final Androlib mAndrolib;

    private IFile mApkFile;
    private IFile mOutDir;
    private ResTable mResTable;
    private short mDecodeResources = DECODE_RESOURCES_FULL;
    private boolean mForceDelete = false;
    private boolean mKeepBrokenResources = false;
    private boolean mBakDeb = true;
    private Collection<String> mUncompressedFiles;
    private boolean mAnalysisMode = false;
    private int mApi = 15;
}
