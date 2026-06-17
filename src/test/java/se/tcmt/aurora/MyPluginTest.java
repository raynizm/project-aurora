package se.tcmt.aurora;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.PsiErrorElementUtil;
import org.jetbrains.annotations.NotNull;
import se.tcmt.aurora.services.PluginService;

@TestDataPath("$CONTENT_ROOT/src/test/testData")
public class MyPluginTest extends BasePlatformTestCase {

    public void testXMLFile() {
        Object psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>");
        XmlFile xmlFile = (XmlFile) psiFile;

        assertFalse(PsiErrorElementUtil.hasErrors(getProject(), xmlFile.getVirtualFile()));

        if (xmlFile.getRootTag() != null) {
            assertEquals("foo", xmlFile.getRootTag().getName());
            assertEquals("bar", xmlFile.getRootTag().getValue().getText());
        }
    }

    public void testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2");
    }

    public void testProjectService() {
        PluginService projectService = getProject().getService(PluginService.class);

        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber());
    }

    @NotNull
    @Override
    public String getTestDataPath() {
        return "src/test/testData/rename";
    }
}
