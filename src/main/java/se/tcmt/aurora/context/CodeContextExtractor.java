package se.tcmt.aurora.context;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts context from the current editor for AI suggestions.
 */
public class CodeContextExtractor {

    private static final Logger LOG = Logger.getInstance(CodeContextExtractor.class);
    private static final int CONTEXT_LINES_ABOVE = 10;
    private static final int CONTEXT_LINES_BELOW = 10;
    private static final int MAX_CONTEXT_SIZE = 4000; // Limit context size

    /**
     * Extracts relevant code context from the current editor.
     */
    @NotNull
    public static Context extractContext(@NotNull Project project, @Nullable Editor editor) {
        Context context = new Context();

        if (editor == null) {
            LOG.debug("No editor available for context extraction");
            return context;
        }

        PsiFile file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) {
            LOG.debug("No PSI file for current document");
            return context;
        }

        // Set file info
        context.setFileName(file.getName());
        context.setLanguage(file.getLanguage().getDisplayName());
        if (file.getVirtualFile() != null) {
            context.setFilePath(file.getVirtualFile().getPath());
        }

        // Extract code around cursor
        int offset = editor.getCaretModel().getOffset();
        int lineNumber = editor.getDocument().getLineNumber(offset);
        
        int startLine = Math.max(0, lineNumber - CONTEXT_LINES_ABOVE);
        int endLine = Math.min(lineNumber + CONTEXT_LINES_BELOW, editor.getDocument().getLineCount() - 1);

        StringBuilder codeContext = new StringBuilder();
        
        // Add method/class context if available
        PsiElement element = file.findElementAt(offset);
        if (element != null) {
            PsiMethod method = findParentMethod(element);
            if (method != null && method.getTextLength() < 2000) {
                codeContext.append("/* Method: ").append(method.getName()).append(" */\n");
                codeContext.append(method.getText()).append("\n\n");
            }

            PsiClass clazz = findParentClass(element);
            if (clazz != null && method == null) {
                codeContext.append("/* Class: ").append(clazz.getName() != null ? clazz.getName() : "unknown").append(" */\n");
            }
        }

        // Add surrounding code lines with line numbers
        for (int i = startLine; i <= endLine; i++) {
            int startOffset = editor.getDocument().getLineStartOffset(i);
            int endOffset = editor.getDocument().getLineEndOffset(i);
            String lineText = editor.getDocument().getText(new com.intellij.openapi.util.TextRange(startOffset, endOffset));
            
            // Truncate very long lines
            if (lineText.length() > 200) {
                lineText = lineText.substring(0, 200) + "...";
            }
            
            codeContext.append(String.format("%3d | %s\n", i + 1, lineText));
        }

        context.setCodeSnippet(codeContext.toString());
        
        // Get selected text if any
        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        if (selectionEnd > selectionStart) {
            String selectedText = editor.getDocument().getText(new com.intellij.openapi.util.TextRange(selectionStart, selectionEnd));
            context.setSelectedCode(selectedText);
        }

        // Limit total context size
        if (codeContext.length() > MAX_CONTEXT_SIZE) {
            context.setCodeSnippet(codeContext.substring(0, MAX_CONTEXT_SIZE) + "\n... [truncated]");
        }

        LOG.debug("Extracted context from " + file.getName() + ": " + codeContext.length() + " chars");
        return context;
    }

    @Nullable
    private static PsiMethod findParentMethod(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiMethod) {
                return (PsiMethod) current;
            }
            current = current.getParent();
        }
        return null;
    }

    @Nullable
    private static PsiClass findParentClass(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiClass) {
                return (PsiClass) current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Represents extracted code context.
     */
    public static class Context {
        private String fileName;
        private String language;
        private String filePath;
        private String codeSnippet;
        private String selectedCode;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getCodeSnippet() { return codeSnippet; }
        public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }

        public String getSelectedCode() { return selectedCode; }
        public void setSelectedCode(String selectedCode) { this.selectedCode = selectedCode; }

        /**
         * Formats the context as a string to prepend to user messages.
         */
        @NotNull
        public String formatForPrompt() {
            StringBuilder sb = new StringBuilder();
            
            if (fileName != null && !fileName.isEmpty()) {
                sb.append("[Context: ").append(fileName);
                if (language != null && !language.isEmpty()) {
                    sb.append(" (").append(language).append(")");
                }
                sb.append("]\n\n");
            }

            if (codeSnippet != null && !codeSnippet.isEmpty()) {
                sb.append("```java\n").append(codeSnippet).append("```\n\n");
            }

            if (selectedCode != null && !selectedCode.isEmpty()) {
                sb.append("[Selected Code]\n```\n").append(selectedCode).append("```\n\n");
            }

            return sb.toString();
        }

        public boolean hasContext() {
            return codeSnippet != null && !codeSnippet.isEmpty();
        }
    }
}
