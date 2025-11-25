package com.ebook.reader.service;

import com.ebook.reader.model.Book;
import com.ebook.reader.controller.ReaderController.TocItem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class EpubService {

    private String currentTempDir = null;

    /**
     * Get chapter-to-filename mapping for navigation
     */
    public Map<String, Integer> buildChapterFileMap(String filePath, List<String> chapters) {
        Map<String, Integer> fileMap = new HashMap<>();
        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(new File(filePath));
            ZipEntry opfEntry = findOpfFile(zipFile);

            if (opfEntry != null) {
                Document opfDoc = parseXmlEntry(zipFile, opfEntry);
                Map<String, String> manifest = buildManifestWithNamespace(opfDoc);
                String opfFolder = getParentFolder(opfEntry.getName());

                NodeList spineItems = opfDoc.getElementsByTagName("itemref");
                if (spineItems.getLength() == 0) {
                    spineItems = opfDoc.getElementsByTagNameNS("*", "itemref");
                }

                for (int i = 0; i < spineItems.getLength(); i++) {
                    Element itemref = (Element) spineItems.item(i);
                    String idref = itemref.getAttribute("idref");

                    if (idref != null && !idref.isEmpty()) {
                        String href = manifest.get(idref);
                        if (href != null) {
                            String fullPath = normalizePath(opfFolder, href);
                            fileMap.put(fullPath, i);
                            fileMap.put(href, i);

                            String filename = href.contains("/") ?
                                    href.substring(href.lastIndexOf("/") + 1) : href;
                            fileMap.put(filename, i);

                            if (filename.contains(".")) {
                                String nameOnly = filename.substring(0, filename.lastIndexOf("."));
                                fileMap.put(nameOnly, i);
                            }

                            fileMap.put(href + "#", i);
                            fileMap.put(filename + "#", i);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error building chapter file map: " + e.getMessage());
        } finally {
            closeZipFile(zipFile);
        }

        return fileMap;
    }

    public Book extractMetadata(String filePath) {
        Book book = new Book();
        book.setFilePath(filePath);
        book.setFileType("EPUB");

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(new File(filePath));

            ZipEntry opfEntry = findOpfFile(zipFile);
            if (opfEntry != null) {
                Document opfDoc = parseXmlEntry(zipFile, opfEntry);

                String title = getMetadataValueWithNamespace(opfDoc, "title");
                if (title == null || title.trim().isEmpty()) {
                    title = new File(filePath).getName().replaceFirst("[.][^.]+$", "");
                }
                book.setTitle(title);

                String author = getMetadataValueWithNamespace(opfDoc, "creator");
                if (author == null || author.trim().isEmpty()) {
                    author = "Unknown Author";
                }
                book.setAuthor(author);

                NodeList spineItems = opfDoc.getElementsByTagName("itemref");
                if (spineItems.getLength() == 0) {
                    spineItems = opfDoc.getElementsByTagNameNS("*", "itemref");
                }
                book.setTotalPages(spineItems.getLength());

                String coverPath = extractCover(zipFile, opfDoc, book.getTitle());
                book.setCoverPath(coverPath);

                System.out.println("✓ EPUB metadata: " + book.getTitle() + " by " + book.getAuthor());
            } else {
                System.err.println("✗ Could not find OPF file in EPUB");
                setFallbackMetadata(book, filePath);
            }

        } catch (Exception e) {
            System.err.println("✗ Error extracting EPUB metadata: " + e.getMessage());
            e.printStackTrace();
            setFallbackMetadata(book, filePath);
        } finally {
            closeZipFile(zipFile);
        }

        return book;
    }

    private void setFallbackMetadata(Book book, String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        book.setTitle(fileName.replaceFirst("[.][^.]+$", ""));
        book.setAuthor("Unknown Author");
        book.setTotalPages(1);
    }

    /**
     * CALIBRE-STYLE: Extract EPUB to temp directory for better image handling
     */
    private String extractEpubToTemp(String epubPath) {
        try {
            // Create unique temp directory for this EPUB
            String tempBase = System.getProperty("java.io.tmpdir");
            String epubName = new File(epubPath).getName().replaceAll("[^a-zA-Z0-9.-]", "_");
            String tempDir = tempBase + File.separator + "ebook-reader-" + epubName + "-" + System.currentTimeMillis();

            File tempDirFile = new File(tempDir);
            if (!tempDirFile.exists()) {
                tempDirFile.mkdirs();
            }

            System.out.println("→ Extracting EPUB to: " + tempDir);

            // Extract all files
            ZipFile zipFile = new ZipFile(new File(epubPath));
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int fileCount = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(tempDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fileCount++;
                    }
                }
            }

            zipFile.close();
            System.out.println("✓ Extracted " + fileCount + " files to temp directory");

            currentTempDir = tempDir;
            return tempDir;

        } catch (Exception e) {
            System.err.println("✗ Error extracting EPUB: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Clean up temp directory
     */
    public void cleanupTempDir() {
        if (currentTempDir != null) {
            try {
                deleteDirectory(new File(currentTempDir));
                System.out.println("✓ Cleaned up temp directory: " + currentTempDir);
                currentTempDir = null;
            } catch (Exception e) {
                System.err.println("⚠ Could not delete temp directory: " + e.getMessage());
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        dir.delete();
    }

    public List<String> getChapterContents(String filePath) {
        List<String> chapters = new ArrayList<>();

        // Extract EPUB to temp directory first
        String tempDir = extractEpubToTemp(filePath);
        if (tempDir == null) {
            chapters.add("<html><body><h1>Error: Could not extract EPUB</h1></body></html>");
            return chapters;
        }

        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(new File(filePath));
            ZipEntry opfEntry = findOpfFile(zipFile);

            if (opfEntry == null) {
                System.err.println("✗ OPF file not found in EPUB");
                chapters.add("<html><body><h1>Error: Could not read EPUB file</h1></body></html>");
                return chapters;
            }

            Document opfDoc = parseXmlEntry(zipFile, opfEntry);

            NodeList spineItems = opfDoc.getElementsByTagName("itemref");
            if (spineItems.getLength() == 0) {
                spineItems = opfDoc.getElementsByTagNameNS("*", "itemref");
            }

            Map<String, String> manifest = buildManifestWithNamespace(opfDoc);
            String opfFolder = getParentFolder(opfEntry.getName());

            System.out.println("→ Reading " + spineItems.getLength() + " chapters from EPUB");

            for (int i = 0; i < spineItems.getLength(); i++) {
                Element itemref = (Element) spineItems.item(i);
                String idref = itemref.getAttribute("idref");

                if (idref == null || idref.isEmpty()) continue;

                String href = manifest.get(idref);
                if (href != null) {
                    String fullPath = normalizePath(opfFolder, href);
                    ZipEntry chapterEntry = findChapterEntry(zipFile, fullPath, opfFolder, href);

                    if (chapterEntry != null) {
                        String content = readZipEntry(zipFile, chapterEntry);
                        if (content != null && !content.trim().isEmpty()) {
                            // Process images using temp directory
                            content = processImagesWithTempDir(content, tempDir, opfFolder);
                            chapters.add(content);
                            System.out.println("✓ Chapter " + (i + 1) + ": " + chapterEntry.getName());
                        } else {
                            chapters.add("<html><body><h2>Chapter " + (i + 1) + "</h2><p>No content</p></body></html>");
                        }
                    } else {
                        System.err.println("✗ Chapter file not found: " + fullPath);
                        chapters.add("<html><body><h2>Chapter " + (i + 1) + "</h2><p>File not found</p></body></html>");
                    }
                }
            }

            if (chapters.isEmpty()) {
                chapters.add("<html><body><h1>No Content</h1><p>Could not load chapters.</p></body></html>");
            }

        } catch (Exception e) {
            System.err.println("✗ Error reading EPUB chapters: " + e.getMessage());
            e.printStackTrace();
            chapters.clear();
            chapters.add("<html><body><h1>Error</h1><p>" + e.getMessage() + "</p></body></html>");
        } finally {
            closeZipFile(zipFile);
        }

        return chapters;
    }

    /**
     * IMPROVED: Process images using extracted temp directory (Calibre-style)
     */
    private String processImagesWithTempDir(String htmlContent, String tempDir, String opfFolder) {
        if (htmlContent == null || htmlContent.isEmpty()) return htmlContent;

        try {
            String processed = htmlContent;
            int imageCount = 0;
            int successCount = 0;

            // Patterns for finding images
            String[] imgPatterns = {
                    "(?i)<img([^>]*?)src\\s*=\\s*[\"']([^\"']+)[\"']([^>]*?)>",
                    "(?i)<image([^>]*?)href\\s*=\\s*[\"']([^\"']+)[\"']([^>]*?)>",
                    "(?i)<image([^>]*?)xlink:href\\s*=\\s*[\"']([^\"']+)[\"']([^>]*?)>"
            };

            for (String patternStr : imgPatterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(processed);
                StringBuffer result = new StringBuffer();

                while (matcher.find()) {
                    imageCount++;
                    String beforeSrc = matcher.group(1);
                    String imgSrc = matcher.group(2);
                    String afterSrc = matcher.group(3);

                    // Skip data URLs
                    if (imgSrc.startsWith("data:")) {
                        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                        continue;
                    }

                    try {
                        // Find image file in temp directory
                        File imageFile = findImageInTempDir(tempDir, imgSrc, opfFolder);

                        if (imageFile != null && imageFile.exists()) {
                            // Convert to file:// URL
                            String fileUrl = imageFile.toURI().toString();

                            // Replace with file URL
                            String newTag;
                            if (patternStr.contains("xlink:href")) {
                                newTag = String.format("<image%sxlink:href=\"%s\"%s>",
                                        beforeSrc, fileUrl, afterSrc);
                            } else if (patternStr.contains("<image")) {
                                newTag = String.format("<image%shref=\"%s\"%s>",
                                        beforeSrc, fileUrl, afterSrc);
                            } else {
                                newTag = String.format("<img%ssrc=\"%s\"%s>",
                                        beforeSrc, fileUrl, afterSrc);
                            }

                            matcher.appendReplacement(result, Matcher.quoteReplacement(newTag));
                            successCount++;
                            System.out.println("✓ Linked image " + successCount + ": " + imgSrc + " → " + imageFile.getName());
                        } else {
                            System.err.println("⚠ Image not found: " + imgSrc);
                            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                        }
                    } catch (Exception e) {
                        System.err.println("✗ Error processing image " + imgSrc + ": " + e.getMessage());
                        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                    }
                }

                matcher.appendTail(result);
                processed = result.toString();
            }

            if (imageCount > 0) {
                System.out.println("✓ Image processing: " + successCount + "/" + imageCount + " images linked successfully");
            }

            return processed;

        } catch (Exception e) {
            System.err.println("✗ Error processing images: " + e.getMessage());
            e.printStackTrace();
            return htmlContent;
        }
    }

    /**
     * Find image file in extracted temp directory
     */
    private File findImageInTempDir(String tempDir, String imgSrc, String opfFolder) {
        // Clean up the image source path
        String cleanSrc = imgSrc;

        // Remove URL fragments
        if (cleanSrc.contains("#")) {
            cleanSrc = cleanSrc.substring(0, cleanSrc.indexOf("#"));
        }

        // Remove query strings
        if (cleanSrc.contains("?")) {
            cleanSrc = cleanSrc.substring(0, cleanSrc.indexOf("?"));
        }

        // Decode URL encoding
        try {
            cleanSrc = java.net.URLDecoder.decode(cleanSrc, "UTF-8");
        } catch (Exception e) {
            // Ignore
        }

        // Try multiple path variations
        String[] pathVariations = {
                cleanSrc,
                cleanSrc.replaceFirst("^/+", ""),
                normalizePath(opfFolder, cleanSrc),
                "OEBPS/" + cleanSrc,
                "EPUB/" + cleanSrc,
                "OPS/" + cleanSrc,
                "images/" + cleanSrc,
                "Images/" + cleanSrc,
                "img/" + cleanSrc
        };

        for (String path : pathVariations) {
            File imageFile = new File(tempDir, path);
            if (imageFile.exists() && imageFile.isFile()) {
                System.out.println("  → Found: " + path);
                return imageFile;
            }
        }

        // Try case-insensitive search as last resort
        return findImageCaseInsensitive(new File(tempDir), cleanSrc);
    }

    /**
     * Case-insensitive recursive file search
     */
    private File findImageCaseInsensitive(File dir, String fileName) {
        if (!dir.isDirectory()) return null;

        String targetName = new File(fileName).getName().toLowerCase();

        File[] files = dir.listFiles();
        if (files == null) return null;

        // First pass: direct children
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().equals(targetName)) {
                return file;
            }
        }

        // Second pass: recursive search
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findImageCaseInsensitive(file, fileName);
                if (found != null) return found;
            }
        }

        return null;
    }

    public List<TocItem> getTableOfContentsWithMapping(String filePath, List<String> chapters) {
        List<TocItem> toc = new ArrayList<>();
        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(new File(filePath));
            ZipEntry opfEntry = findOpfFile(zipFile);

            if (opfEntry == null) {
                for (int i = 0; i < chapters.size(); i++) {
                    toc.add(new TocItem("Chapter " + (i + 1), i));
                }
                return toc;
            }

            Document opfDoc = parseXmlEntry(zipFile, opfEntry);

            ZipEntry ncxEntry = findFileInZip(zipFile, "toc.ncx");
            if (ncxEntry == null) {
                ncxEntry = findFileInZip(zipFile, ".ncx");
            }

            if (ncxEntry != null) {
                Document ncxDoc = parseXmlEntry(zipFile, ncxEntry);
                NodeList navPoints = ncxDoc.getElementsByTagName("navPoint");
                if (navPoints.getLength() == 0) {
                    navPoints = ncxDoc.getElementsByTagNameNS("*", "navPoint");
                }

                Map<String, Integer> spineOrderMap = buildSpineOrderMap(opfDoc);

                for (int i = 0; i < navPoints.getLength(); i++) {
                    Element navPoint = (Element) navPoints.item(i);

                    NodeList textNodes = navPoint.getElementsByTagName("text");
                    if (textNodes.getLength() == 0) {
                        textNodes = navPoint.getElementsByTagNameNS("*", "text");
                    }

                    String title = "Chapter " + (i + 1);
                    if (textNodes.getLength() > 0) {
                        title = textNodes.item(0).getTextContent().trim();
                    }

                    NodeList contentNodes = navPoint.getElementsByTagName("content");
                    if (contentNodes.getLength() == 0) {
                        contentNodes = navPoint.getElementsByTagNameNS("*", "content");
                    }

                    int pageIndex = i;
                    if (contentNodes.getLength() > 0) {
                        Element content = (Element) contentNodes.item(0);
                        String src = content.getAttribute("src");

                        if (src != null && !src.isEmpty()) {
                            if (src.contains("#")) {
                                src = src.substring(0, src.indexOf("#"));
                            }

                            Integer order = spineOrderMap.get(src);
                            if (order != null && order >= 0 && order < chapters.size()) {
                                pageIndex = order;
                            }
                        }
                    }

                    toc.add(new TocItem(title, pageIndex));
                }
            }

            if (toc.isEmpty()) {
                for (int i = 0; i < chapters.size(); i++) {
                    toc.add(new TocItem("Chapter " + (i + 1), i));
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Error reading EPUB TOC: " + e.getMessage());
            e.printStackTrace();

            for (int i = 0; i < chapters.size(); i++) {
                toc.add(new TocItem("Chapter " + (i + 1), i));
            }
        } finally {
            closeZipFile(zipFile);
        }

        return toc;
    }

    private Map<String, Integer> buildSpineOrderMap(Document opfDoc) {
        Map<String, Integer> orderMap = new HashMap<>();

        try {
            Map<String, String> manifest = buildManifestWithNamespace(opfDoc);

            NodeList spineItems = opfDoc.getElementsByTagName("itemref");
            if (spineItems.getLength() == 0) {
                spineItems = opfDoc.getElementsByTagNameNS("*", "itemref");
            }

            for (int i = 0; i < spineItems.getLength(); i++) {
                Element itemref = (Element) spineItems.item(i);
                String idref = itemref.getAttribute("idref");

                if (idref != null && !idref.isEmpty()) {
                    String href = manifest.get(idref);
                    if (href != null) {
                        String filename = href;
                        if (filename.contains("/")) {
                            filename = filename.substring(filename.lastIndexOf("/") + 1);
                        }
                        orderMap.put(filename, i);
                        orderMap.put(href, i);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error building spine order map: " + e.getMessage());
        }

        return orderMap;
    }

    // REPLACE convertToStyledHtml() method in EpubService with this UPDATED version:

    // ADD to EpubService.java

    // ADD to EpubService.java

    /**
     * UPDATED: Convert HTML to styled HTML with custom font family CSS injection
     */
    public String convertToStyledHtml(String htmlContent, String theme, int fontSize,
                                      String fontFamily, String basePath) {
        // Theme colors
        String backgroundColor, textColor, linkColor;

        switch (theme.toLowerCase()) {
            case "dark" -> {
                backgroundColor = "#1e1e1e";
                textColor = "#e0e0e0";
                linkColor = "#64b5f6";
            }
            case "sepia" -> {
                backgroundColor = "#f4ecd8";
                textColor = "#5c4a3a";
                linkColor = "#8b4513";
            }
            default -> { // light
                backgroundColor = "#ffffff";
                textColor = "#2c3e50";
                linkColor = "#3498db";
            }
        }

        // NEW: Get custom font CSS if it's a custom family
        String customFontCSS = "";
        try {
            com.ebook.reader.dao.FontFamilyDAO fontDAO = new com.ebook.reader.dao.FontFamilyDAO();
            // ✅ FIX: Get FontFamily object from database by name
            com.ebook.reader.dao.FontFamilyDAO.FontFamily fontFamilyObj = fontDAO.getFontFamily(fontFamily);

            if (fontFamilyObj != null && fontFamilyObj.cssDeclaration != null) {
                customFontCSS = fontFamilyObj.cssDeclaration;
                System.out.println("✓ Injecting custom font CSS for: " + fontFamilyObj.familyName);
            }
        } catch (Exception e) {
            System.err.println("⚠ Could not load custom font CSS: " + e.getMessage());
        }

        // Font family CSS with fallbacks
        String fontFamilyCSS = fontFamily + ", Georgia, 'Times New Roman', serif";

        // Build styled HTML
        StringBuilder styledHtml = new StringBuilder();
        styledHtml.append("<!DOCTYPE html>");
        styledHtml.append("<html><head>");
        styledHtml.append("<meta charset='UTF-8'>");
        styledHtml.append("<base href='file:///").append(basePath.replace("\\", "/")).append("/'>");
        styledHtml.append("<style>");

        // NEW: Inject custom font @font-face declarations first
        if (!customFontCSS.isEmpty()) {
            styledHtml.append("/* Custom Font Family */\n");
            styledHtml.append(customFontCSS);
            styledHtml.append("\n");
        }

        styledHtml.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        styledHtml.append("html, body { ");
        styledHtml.append("  height: 100%; ");
        styledHtml.append("  background-color: ").append(backgroundColor).append("; ");
        styledHtml.append("  color: ").append(textColor).append("; ");
        styledHtml.append("}");
        styledHtml.append("body { ");
        styledHtml.append("  font-family: ").append(fontFamilyCSS).append("; ");
        styledHtml.append("  font-size: ").append(fontSize).append("px; ");
        styledHtml.append("  line-height: 1.8; ");
        styledHtml.append("  padding: 40px 60px; ");
        styledHtml.append("  max-width: 800px; ");
        styledHtml.append("  margin: 0 auto; ");
        styledHtml.append("}");
        styledHtml.append("h1, h2, h3, h4, h5, h6 { ");
        styledHtml.append("  margin-top: 1.5em; ");
        styledHtml.append("  margin-bottom: 0.8em; ");
        styledHtml.append("  font-weight: bold; ");
        styledHtml.append("  line-height: 1.3; ");
        styledHtml.append("}");
        styledHtml.append("h1 { font-size: ").append(fontSize + 12).append("px; }");
        styledHtml.append("h2 { font-size: ").append(fontSize + 8).append("px; }");
        styledHtml.append("h3 { font-size: ").append(fontSize + 4).append("px; }");
        styledHtml.append("p { ");
        styledHtml.append("  margin-bottom: 1em; ");
        styledHtml.append("  text-align: justify; ");
        styledHtml.append("}");

        // NEW: Ensure bold/italic use font variants
        styledHtml.append("b, strong { font-weight: bold; }");
        styledHtml.append("i, em { font-style: italic; }");

        styledHtml.append("a { ");
        styledHtml.append("  color: ").append(linkColor).append("; ");
        styledHtml.append("  text-decoration: none; ");
        styledHtml.append("  cursor: pointer; ");
        styledHtml.append("}");
        styledHtml.append("a:hover { text-decoration: underline; }");
        styledHtml.append("img { ");
        styledHtml.append("  max-width: 100%; ");
        styledHtml.append("  height: auto; ");
        styledHtml.append("  display: block; ");
        styledHtml.append("  margin: 1em auto; ");
        styledHtml.append("}");
        styledHtml.append("blockquote { ");
        styledHtml.append("  margin: 1em 2em; ");
        styledHtml.append("  padding-left: 1em; ");
        styledHtml.append("  border-left: 3px solid ").append(linkColor).append("; ");
        styledHtml.append("  font-style: italic; ");
        styledHtml.append("}");
        styledHtml.append("code, pre { ");
        styledHtml.append("  font-family: 'Courier New', monospace; ");
        styledHtml.append("  background-color: ").append(theme.equals("dark") ? "#2d2d2d" : "#f5f5f5").append("; ");
        styledHtml.append("  padding: 2px 6px; ");
        styledHtml.append("  border-radius: 3px; ");
        styledHtml.append("}");
        styledHtml.append("pre { ");
        styledHtml.append("  padding: 1em; ");
        styledHtml.append("  overflow-x: auto; ");
        styledHtml.append("  margin: 1em 0; ");
        styledHtml.append("}");
        styledHtml.append("</style>");
        styledHtml.append("</head><body>");
        styledHtml.append(htmlContent);
        styledHtml.append("</body></html>");

        return styledHtml.toString();
    }

    // ========== Helper Methods ==========

    private ZipEntry findOpfFile(ZipFile zipFile) {
        ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
        if (containerEntry != null) {
            try {
                Document containerDoc = parseXmlEntry(zipFile, containerEntry);
                NodeList rootfiles = containerDoc.getElementsByTagName("rootfile");
                if (rootfiles.getLength() == 0) {
                    rootfiles = containerDoc.getElementsByTagNameNS("*", "rootfile");
                }

                if (rootfiles.getLength() > 0) {
                    Element rootfile = (Element) rootfiles.item(0);
                    String opfPath = rootfile.getAttribute("full-path");
                    return zipFile.getEntry(opfPath);
                }
            } catch (Exception e) {
                System.err.println("✗ Error reading container.xml: " + e.getMessage());
            }
        }

        return findFileInZip(zipFile, ".opf");
    }

    private ZipEntry findFileInZip(ZipFile zipFile, String extension) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(extension)) {
                return entry;
            }
        }
        return null;
    }

    private Document parseXmlEntry(ZipFile zipFile, ZipEntry entry) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream is = zipFile.getInputStream(entry)) {
            return builder.parse(is);
        }
    }

    private String readZipEntry(ZipFile zipFile, ZipEntry entry) {
        try (InputStream is = zipFile.getInputStream(entry);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            System.err.println("✗ Error reading entry: " + e.getMessage());
            return "";
        }
    }

    private String getMetadataValueWithNamespace(Document doc, String tagName) {
        try {
            NodeList nodes = doc.getElementsByTagName("dc:" + tagName);
            if (nodes.getLength() == 0) {
                nodes = doc.getElementsByTagName(tagName);
            }
            if (nodes.getLength() == 0) {
                nodes = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", tagName);
            }

            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent().trim();
                return value.isEmpty() ? null : value;
            }
        } catch (Exception e) {
            System.err.println("✗ Error getting metadata for " + tagName);
        }
        return null;
    }

    private Map<String, String> buildManifestWithNamespace(Document opfDoc) {
        Map<String, String> manifest = new HashMap<>();
        NodeList items = opfDoc.getElementsByTagName("item");

        if (items.getLength() == 0) {
            items = opfDoc.getElementsByTagNameNS("*", "item");
        }

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id != null && !id.isEmpty() && href != null && !href.isEmpty()) {
                manifest.put(id, href);
            }
        }

        return manifest;
    }

    private ZipEntry findChapterEntry(ZipFile zipFile, String fullPath, String opfFolder, String href) {
        ZipEntry entry = zipFile.getEntry(fullPath);
        if (entry != null) return entry;

        entry = zipFile.getEntry(href);
        if (entry != null) return entry;

        entry = zipFile.getEntry("OEBPS/" + href);
        if (entry != null) return entry;

        String lowerPath = fullPath.toLowerCase();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();
            if (ze.getName().toLowerCase().equals(lowerPath)) {
                return ze;
            }
        }

        return null;
    }

    private String normalizePath(String folder, String file) {
        if (folder.isEmpty()) return file;

        while (file.startsWith("/")) {
            file = file.substring(1);
        }

        if (!folder.endsWith("/")) {
            folder += "/";
        }

        return folder + file;
    }

    private String getParentFolder(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash > 0 ? path.substring(0, lastSlash + 1) : "";
    }

    private String extractCover(ZipFile zipFile, Document opfDoc, String bookTitle) {
        try {
            NodeList items = opfDoc.getElementsByTagName("item");
            if (items.getLength() == 0) {
                items = opfDoc.getElementsByTagNameNS("*", "item");
            }

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String id = item.getAttribute("id").toLowerCase();
                String mediaType = item.getAttribute("media-type");

                if (id.contains("cover") && mediaType != null && mediaType.startsWith("image/")) {
                    String href = item.getAttribute("href");
                    String opfFolder = getParentFolder(findOpfFile(zipFile).getName());
                    String fullPath = normalizePath(opfFolder, href);

                    ZipEntry imageEntry = findChapterEntry(zipFile, fullPath, opfFolder, href);

                    if (imageEntry != null) {
                        String coverPath = saveCoverImage(zipFile, imageEntry, bookTitle);
                        if (coverPath != null) {
                            return coverPath;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error extracting cover: " + e.getMessage());
        }

        return null;
    }

    private String saveCoverImage(ZipFile zipFile, ZipEntry imageEntry, String bookTitle) {
        try {
            String userHome = System.getProperty("user.home");
            String coversDir = userHome + File.separator + ".ebook-reader" + File.separator + "covers";
            File coversDirFile = new File(coversDir);
            if (!coversDirFile.exists()) {
                coversDirFile.mkdirs();
            }

            String safeTitle = bookTitle.replaceAll("[^a-zA-Z0-9.-]", "_");
            String ext = imageEntry.getName().substring(imageEntry.getName().lastIndexOf('.'));
            String coverPath = coversDir + File.separator + safeTitle + "_cover" + ext;

            try (InputStream is = zipFile.getInputStream(imageEntry);
                 FileOutputStream fos = new FileOutputStream(coverPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            return coverPath;

        } catch (Exception e) {
            System.err.println("✗ Error saving cover: " + e.getMessage());
        }

        return null;
    }

    private void closeZipFile(ZipFile zipFile) {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException e) {
                System.err.println("✗ Error closing zip: " + e.getMessage());
            }
        }
    }
}