# 📚 Transparent Ebook Reader

![Screenshot 1](images/1.png)
![Screenshot 2](images/2.png)
![Screenshot 3](images/3.png)
![Screenshot 4](images/4.png)

A modern, feature-rich desktop ebook reader built with JavaFX.  
This document includes: Features • Installation • Usage • Building • Contributing

---

## 🚀 Installation

### **Prerequisites**
- Java 17 or higher  
- Windows 10/11 (for MSI installer)  
- Linux/macOS (for JAR execution)

---

### **Windows Installer (Recommended)**

1. Download `EbookReader-1.0.msi` from Releases  
2. Run the installer  
3. Follow the setup wizard  
4. Launch from Start Menu or Desktop shortcut  

---


# 💻 Usage

## **Adding Books**
- Click **Add Book**
- Select one or multiple EPUB/PDF files
- Books are automatically imported with metadata

---

## **Reading**
- Click any book to open  
- Use ← / → or Page Up / Page Down  
- Press **F11** for fullscreen  
- Press **ESC** to exit fullscreen  

---

## **Keyboard Shortcuts**

| Key | Action |
|-----|--------|
| ← / → | Previous / Next page |
| Page Up / Page Down | Scroll pages |
| Ctrl + B | Add bookmark |
| F11 | Toggle fullscreen |
| ESC | Back / Exit |

---

## **Customizing Settings**
- Click **Settings**
- Choose theme: Light / Dark / Sepia  
- Adjust font size  
- Changes apply instantly  

---

# 🛠️ Building from Source

### **Prerequisites**
- JDK 17+
- Maven 3.8+
- Git

---

### **Clone Repository**
```bash
git clone https://github.com/yourusername/transparent-ebook-reader.git
cd transparent-ebook-reader
```

---

### **Build with Maven**
```bash
mvn clean javafx:run
```

### **Package JAR**
```bash
mvn clean package
```

### **Create Windows installer (JPackage)**
```bash
mvn clean install
```

---

# 📂 Project Structure

```
ebook-reader/
├── src/
│   ├── main/
│   │   ├── java/com/ebook/reader/
│   │   │   ├── controller/
│   │   │   ├── dao/
│   │   │   ├── model/
│   │   │   ├── service/
│   │   │   └── Main.java
│   │   └── resources/
│   │       ├── css/
│   │       ├── fxml/
│   │       └── images/
├── pom.xml
└── README.md
```

---

# 🏗️ Technology Stack

### **Core**
- Java 17  
- JavaFX 21  
- Maven  

### **Libraries**
- Apache PDFBox 3.0  
- SQLite JDBC  
- SLF4J  

### **Architecture**
- MVC Pattern  
- DAO Pattern  
- Observer Pattern  

---


# 🤝 Contributing

### **Steps**
```bash
git checkout -b feature/AmazingFeature
git commit -m "Add AmazingFeature"
git push origin feature/AmazingFeature
```

Open a Pull Request on GitHub.

---

# 🐛 Known Issues
- Large PDFs (>500 pages) may load slowly  
- Some EPUB SVGs render incorrectly  
- Heavy usage may require more RAM  

---

# 📄 License (MIT)

```
MIT License  
Copyright (c) 2025
```

---

# 📞 Contact

**Author:** Hashi  
**Email:** phamthithienha17032005@gmail.com  
**GitHub:** https://github.com/hashi173

---

Thanks for using Transparent Ebook Reader! ❤️
