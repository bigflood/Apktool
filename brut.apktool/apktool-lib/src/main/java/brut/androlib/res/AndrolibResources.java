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

package brut.androlib.res;

import brut.androlib.AndrolibException;
import brut.androlib.res.data.*;
import brut.androlib.res.decoder.*;
import brut.androlib.res.util.ExtMXSerializer;
import brut.androlib.res.util.ExtXmlSerializer;
import brut.directory.*;
import brut.util.Duo;
import org.xmlpull.v1.XmlSerializer;

import java.io.*;
import java.util.logging.Logger;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
final public class AndrolibResources {

    public ResTable getResTable(IFile apkFile, boolean loadMainPkg)
            throws AndrolibException {
        ResTable resTable = new ResTable(this);
        if (loadMainPkg) {
            loadMainPkg(resTable, apkFile);
        }
        return resTable;
    }

    public ResPackage loadMainPkg(ResTable resTable, IFile apkFile)
            throws AndrolibException {
        LOGGER.info("Loading resource table...");
        ResPackage[] pkgs = getResPackagesFromApk(apkFile, resTable, sKeepBroken);
        ResPackage pkg = null;

        switch (pkgs.length) {
            case 1:
                pkg = pkgs[0];
                break;
            case 2:
                if (pkgs[0].getName().equals("android")) {
                    LOGGER.warning("Skipping \"android\" package group");
                    pkg = pkgs[1];
                    break;
                } else if (pkgs[0].getName().equals("com.htc")) {
                    LOGGER.warning("Skipping \"htc\" package group");
                    pkg = pkgs[1];
                    break;
                }

            default:
                pkg = selectPkgWithMostResSpecs(pkgs);
                break;
        }

        if (pkg == null) {
            throw new AndrolibException("arsc files with zero packages or no arsc file found.");
        }

        resTable.addPackage(pkg, true);
        return pkg;
    }

    public ResPackage selectPkgWithMostResSpecs(ResPackage[] pkgs)
        throws AndrolibException {
        int id = 0;
        int value = 0;

        for (ResPackage resPackage : pkgs) {
            if (resPackage.getResSpecCount() > value && ! resPackage.getName().equalsIgnoreCase("android")) {
                value = resPackage.getResSpecCount();
                id = resPackage.getId();
            }
        }

        // if id is still 0, we only have one pkgId which is "android" -> 1
        return (id == 0) ? pkgs[0] : pkgs[1];
    }

    public void decodeManifest(ResTable resTable, IFile apkFile, IFile outDir)
            throws AndrolibException {

        Duo<ResFileDecoder, AXmlResourceParser> duo = getManifestFileDecoder();
        ResFileDecoder fileDecoder = duo.m1;

        // Set ResAttrDecoder
        duo.m2.setAttrDecoder(new ResAttrDecoder());
        ResAttrDecoder attrDecoder = duo.m2.getAttrDecoder();

        // Fake ResPackage
        attrDecoder.setCurrentPackage(new ResPackage(resTable, 0, null));

        Directory inApk;
        Directory out;
        try {
            inApk = apkFile.getDirectory();
            out = outDir.toDirectory();

            LOGGER.info("Decoding AndroidManifest.xml with only framework resources...");
            fileDecoder.decodeManifest(inApk, "AndroidManifest.xml", out, "AndroidManifest.xml");

        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void adjustPackageManifest(ResTable resTable, String filePath)
            throws AndrolibException {

        // compare resources.arsc package name to the one present in AndroidManifest
        ResPackage resPackage = resTable.getCurrentResPackage();
        String packageOriginal = resPackage.getName();
        //mPackageRenamed = resTable.getPackageRenamed();

        resTable.setPackageId(resPackage.getId());
        resTable.setPackageOriginal(packageOriginal);

        // 1) Check if packageOriginal === mPackageRenamed
        // 2) Check if packageOriginal is ignored via IGNORED_PACKAGES
        // 2a) If its ignored, make sure the mPackageRenamed isn't explicitly allowed
//        if (packageOriginal.equalsIgnoreCase(mPackageRenamed) ||
//                (Arrays.asList(IGNORED_PACKAGES).contains(packageOriginal) &&
//                ! Arrays.asList(ALLOWED_PACKAGES).contains(mPackageRenamed))) {
//            LOGGER.info("Regular manifest package...");
//        } else {
//            LOGGER.info("Renamed manifest package found! >> " + mPackageRenamed + " with " + packageOriginal);
//            //ResXmlPatcher.renameManifestPackage(new IFile(filePath), packageOriginal);
//        }
    }

    public void decodeManifestWithResources(ResTable resTable, IFile apkFile, IFile outDir)
            throws AndrolibException {

        Duo<ResFileDecoder, AXmlResourceParser> duo = getResFileDecoder();
        ResFileDecoder fileDecoder = duo.m1;
        ResAttrDecoder attrDecoder = duo.m2.getAttrDecoder();

        attrDecoder.setCurrentPackage(resTable.listMainPackages().iterator().next());

        Directory inApk, in = null, out;
        try {
            inApk = apkFile.getDirectory();
            out = outDir.toDirectory();
            LOGGER.info("Decoding AndroidManifest.xml with resources...");

            fileDecoder.decodeManifest(inApk, "AndroidManifest.xml", out, "AndroidManifest.xml");

            // Remove versionName / versionCode (aapt API 16)
            if (!resTable.getAnalysisMode()) {

                // check for a mismatch between resources.arsc package and the package listed in AndroidManifest
                // also remove the android::versionCode / versionName from manifest for rebuild
                // this is a required change to prevent aapt warning about conflicting versions
                // it will be passed as a parameter to aapt like "--min-sdk-version" via apktool.yml
                adjustPackageManifest(resTable, outDir.getAbsolutePath() + File.separator + "AndroidManifest.xml");

                //ResXmlPatcher.removeManifestVersions(new IFile( outDir.getAbsolutePath() + File.separator + "AndroidManifest.xml"));

                //mPackageId = String.valueOf(resTable.getPackageId());
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }
    }

    public void decode(ResTable resTable, IFile apkFile, IFile outDir)
            throws AndrolibException {
        Duo<ResFileDecoder, AXmlResourceParser> duo = getResFileDecoder();
        ResFileDecoder fileDecoder = duo.m1;
        ResAttrDecoder attrDecoder = duo.m2.getAttrDecoder();

        attrDecoder.setCurrentPackage(resTable.listMainPackages().iterator().next());
        Directory inApk, in = null, out;

        try {
            out = outDir.toDirectory();

            inApk = apkFile.getDirectory();
            out = out.createDir("res");
            if (inApk.containsDir("res")) {
                in = inApk.getDir("res");
            }
            if (in == null && inApk.containsDir("r")) {
                in = inApk.getDir("r");
            }
        } catch (DirectoryException ex) {
            throw new AndrolibException(ex);
        }

        ExtMXSerializer xmlSerializer = getResXmlSerializer();
        for (ResPackage pkg : resTable.listMainPackages()) {
            attrDecoder.setCurrentPackage(pkg);

            LOGGER.info("Decoding file-resources...");
            for (ResResource res : pkg.listFiles()) {
                fileDecoder.decode(res, in, out);
            }

            //LOGGER.info("Decoding values */* XMLs...");
            //for (ResValuesFile valuesFile : pkg.listValuesFiles()) {
            //    generateValuesFile(valuesFile, out, xmlSerializer);
            //}
            generatePublicXml(pkg, out, xmlSerializer);
        }

        AndrolibException decodeError = duo.m2.getFirstError();
        if (decodeError != null) {
            throw decodeError;
        }
    }

    public Duo<ResFileDecoder, AXmlResourceParser> getResFileDecoder() {
        ResStreamDecoderContainer decoders = new ResStreamDecoderContainer();
        decoders.setDecoder("raw", new ResRawStreamDecoder());
        decoders.setDecoder("9patch", new Res9patchStreamDecoder());

        AXmlResourceParser axmlParser = new AXmlResourceParser();
        axmlParser.setAttrDecoder(new ResAttrDecoder());
        decoders.setDecoder("xml", new XmlPullStreamDecoder(axmlParser, getResXmlSerializer()));

        return new Duo<ResFileDecoder, AXmlResourceParser>(new ResFileDecoder(decoders), axmlParser);
    }

    public Duo<ResFileDecoder, AXmlResourceParser> getManifestFileDecoder() {
        ResStreamDecoderContainer decoders = new ResStreamDecoderContainer();

        AXmlResourceParser axmlParser = new AXmlResourceParser();

        decoders.setDecoder("xml", new XmlPullStreamDecoder(axmlParser,getResXmlSerializer()));

        return new Duo<ResFileDecoder, AXmlResourceParser>(new ResFileDecoder(decoders), axmlParser);
    }

    public ExtMXSerializer getResXmlSerializer() {
        ExtMXSerializer serial = new ExtMXSerializer();
        serial.setProperty(ExtXmlSerializer.PROPERTY_SERIALIZER_INDENTATION, "    ");
        serial.setProperty(ExtXmlSerializer.PROPERTY_SERIALIZER_LINE_SEPARATOR, System.getProperty("line.separator"));
        serial.setProperty(ExtXmlSerializer.PROPERTY_DEFAULT_ENCODING, "utf-8");
        serial.setDisabledAttrEscape(true);
        return serial;
    }

    private void generatePublicXml(ResPackage pkg, Directory out,
                                   XmlSerializer serial) throws AndrolibException {
        try {
            OutputStream outStream = out.getFileOutput("values/public.xml");
            serial.setOutput(outStream, null);
            serial.startDocument(null, null);
            serial.startTag(null, "resources");

            for (ResResSpec spec : pkg.listResSpecs()) {
                serial.startTag(null, "public");
                serial.attribute(null, "type", spec.getType().getName());
                serial.attribute(null, "name", spec.getName());
                serial.attribute(null, "id", String.format("0x%08x", spec.getId().id));
                serial.endTag(null, "public");
            }

            serial.endTag(null, "resources");
            serial.endDocument();
            serial.flush();
            outStream.close();
        } catch (IOException | DirectoryException ex) {
            throw new AndrolibException("Could not generate public.xml file", ex);
        }
    }

    private ResPackage[] getResPackagesFromApk(IFile apkFile,ResTable resTable, boolean keepBroken)
            throws AndrolibException {
        try {
            BufferedInputStream bfi = new BufferedInputStream(apkFile.getDirectory().getFileInput("resources.arsc"));
            return ARSCDecoder.decode(bfi, false, keepBroken, resTable).getPackages();
        } catch (DirectoryException ex) {
            throw new AndrolibException("Could not load resources.arsc from file: " + apkFile, ex);
        }
    }

    // TODO: dirty static hack. I have to refactor decoding mechanisms.
    public static boolean sKeepBroken = false;

    private final static Logger LOGGER = Logger.getLogger(AndrolibResources.class.getName());

    private final static String[] IGNORED_PACKAGES = new String[] {
            "android", "com.htc", "miui", "com.lge", "com.lge.internal", "yi", "com.miui.core", "flyme",
            "air.com.adobe.appentry" };

    private final static String[] ALLOWED_PACKAGES = new String[] {
            "com.miui" };
}
