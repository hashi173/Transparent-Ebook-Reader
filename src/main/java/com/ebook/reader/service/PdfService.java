package com.ebook.reader.service;

import com.ebook.reader.model.Book;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Fixed PDF Service with proper resource management and error handling
 */
public class PdfService {

    /**
     * Extract metadata from PDF file
     */
    public Book extractMetadata(String filePath) {
        Book book = new Book();
        book.setFilePath(filePath);
        book.setFileType("PDF");

        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            setFallbackMetadata(book, filePath);
            return book;
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);

            // Get document information
            PDDocumentInformation info = document.getDocumentInformation();

            // Extract title
            String title = info.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = pdfFile.getName().replaceFirst("[.][^.]+$", "");
            }
            book.setTitle(title);

            // Extract author
            String author = info.getAuthor();
            book.setAuthor(author != null && !author.trim().isEmpty() ? author : "Unknown Author");

            // Get total pages
            int pageCount = document.getNumberOfPages();
            book.setTotalPages(pageCount);

            System.out.println("✓ PDF metadata: " + book.getTitle() + " (" + pageCount + " pages) by " + book.getAuthor());

            // Generate cover from first page
            if (pageCount > 0) {
                String coverPath = generateCoverFromFirstPage(document, title);
                book.setCoverPath(coverPath);
            }

        } catch (IOException e) {
            System.err.println("✗ Error extracting PDF metadata: " + e.getMessage());
            e.printStackTrace();
            setFallbackMetadata(book, filePath);
        } finally {
            closeDocument(document);
        }

        return book;
    }

    private void setFallbackMetadata(Book book, String filePath) {
        File file = new File(filePath);
        book.setTitle(file.getName());
        book.setAuthor("Unknown Author");
        book.setTotalPages(0);
    }

    /**
     * Render a specific page to an Image - FIXED VERSION
     */
    public Image renderPage(String filePath, int pageIndex, float scale) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            return null;
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);

            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                System.err.println("✗ Invalid page index: " + pageIndex + " (total: " + document.getNumberOfPages() + ")");
                return null;
            }

            PDFRenderer renderer = new PDFRenderer(document);
            float dpi = 96 * scale; // Use 96 as base DPI for better quality

            System.out.println("→ Rendering PDF page " + (pageIndex + 1) + " at " + dpi + " DPI...");

            BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, dpi);

            if (bufferedImage == null) {
                System.err.println("✗ Failed to render page " + (pageIndex + 1));
                return null;
            }

            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

            if (fxImage == null) {
                System.err.println("✗ Failed to convert BufferedImage to FX Image");
                return null;
            }

            System.out.println("✓ Rendered PDF page " + (pageIndex + 1) + " successfully");
            return fxImage;

        } catch (IOException e) {
            System.err.println("✗ Error rendering PDF page " + (pageIndex + 1) + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("✗ Unexpected error rendering PDF page: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            closeDocument(document);
        }
    }

    /**
     * Extract text from a specific page
     */
    public String extractTextFromPage(String filePath, int pageIndex) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            return "";
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);

            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                System.err.println("✗ Invalid page index: " + pageIndex);
                return "";
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return stripper.getText(document);

        } catch (IOException e) {
            System.err.println("✗ Error extracting PDF text: " + e.getMessage());
            e.printStackTrace();
            return "";
        } finally {
            closeDocument(document);
        }
    }

    /**
     * Extract text from entire document
     */
    public String extractAllText(String filePath) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            return "";
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);

        } catch (IOException e) {
            System.err.println("✗ Error extracting PDF text: " + e.getMessage());
            e.printStackTrace();
            return "";
        } finally {
            closeDocument(document);
        }
    }

    /**
     * Search for text in PDF
     */
    public boolean searchInPage(String filePath, int pageIndex, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return false;
        }
        String pageText = extractTextFromPage(filePath, pageIndex);
        return pageText.toLowerCase().contains(searchText.toLowerCase());
    }

    /**
     * Get total page count
     */
    public int getPageCount(String filePath) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            return 0;
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            int pageCount = document.getNumberOfPages();
            System.out.println("✓ PDF has " + pageCount + " pages");
            return pageCount;

        } catch (IOException e) {
            System.err.println("✗ Error getting PDF page count: " + e.getMessage());
            e.printStackTrace();
            return 0;
        } finally {
            closeDocument(document);
        }
    }

    /**
     * Generate cover image from first page
     */
    private String generateCoverFromFirstPage(PDDocument document, String bookTitle) {
        if (document == null || document.getNumberOfPages() == 0) {
            return null;
        }

        try {
            String userHome = System.getProperty("user.home");
            String coversDir = userHome + File.separator + ".ebook-reader" + File.separator + "covers";
            File coversDirFile = new File(coversDir);
            if (!coversDirFile.exists()) {
                coversDirFile.mkdirs();
            }

            // Create safe filename from book title
            String safeTitle = bookTitle.replaceAll("[^a-zA-Z0-9.-]", "_");
            if (safeTitle.length() > 50) {
                safeTitle = safeTitle.substring(0, 50);
            }
            String coverPath = coversDir + File.separator + safeTitle + "_cover.png";

            // Check if cover already exists
            File coverFile = new File(coverPath);
            if (coverFile.exists()) {
                System.out.println("✓ Using existing cover: " + coverPath);
                return coverPath;
            }

            // Render first page as cover with higher DPI
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);

            if (image == null) {
                System.err.println("✗ Failed to render cover image");
                return null;
            }

            // Save as PNG
            boolean saved = ImageIO.write(image, "PNG", coverFile);

            if (saved) {
                System.out.println("✓ Generated PDF cover: " + coverPath);
                return coverPath;
            } else {
                System.err.println("✗ Failed to save cover image");
                return null;
            }

        } catch (IOException e) {
            System.err.println("✗ Error generating PDF cover: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get PDF document outline/bookmarks (table of contents)
     */
    public String getOutline(String filePath) {
        StringBuilder outline = new StringBuilder();
        File pdfFile = new File(filePath);

        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.err.println("✗ PDF file does not exist or cannot be read: " + filePath);
            return "Error: Cannot read PDF file";
        }

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            var docOutline = document.getDocumentCatalog().getDocumentOutline();

            if (docOutline != null) {
                var child = docOutline.getFirstChild();
                int count = 0;

                while (child != null && count < 100) {
                    String title = child.getTitle();
                    if (title != null && !title.trim().isEmpty()) {
                        outline.append("• ").append(title.trim()).append("\n");
                    }

                    // Add nested items
                    var nestedChild = child.getFirstChild();
                    int nestedCount = 0;
                    while (nestedChild != null && nestedCount < 50) {
                        String nestedTitle = nestedChild.getTitle();
                        if (nestedTitle != null && !nestedTitle.trim().isEmpty()) {
                            outline.append("  ◦ ").append(nestedTitle.trim()).append("\n");
                        }
                        nestedChild = nestedChild.getNextSibling();
                        nestedCount++;
                    }

                    child = child.getNextSibling();
                    count++;
                }

                if (outline.length() == 0) {
                    outline.append("No table of contents available\n");
                    outline.append("This PDF has ").append(document.getNumberOfPages()).append(" pages");
                } else {
                    System.out.println("✓ Loaded PDF outline with " + count + " items");
                }

            } else {
                outline.append("No table of contents available\n");
                outline.append("This PDF has ").append(document.getNumberOfPages()).append(" pages");
                System.out.println("→ PDF has no outline/bookmarks");
            }

        } catch (IOException e) {
            System.err.println("✗ Error reading PDF outline: " + e.getMessage());
            e.printStackTrace();
            outline.append("Error reading table of contents: ").append(e.getMessage());
        } finally {
            closeDocument(document);
        }

        return outline.toString();
    }

    /**
     * Safely close PDF document
     */
    private void closeDocument(PDDocument document) {
        if (document != null) {
            try {
                document.close();
            } catch (IOException e) {
                System.err.println("✗ Error closing PDF document: " + e.getMessage());
            }
        }
    }
}