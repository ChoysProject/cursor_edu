import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ui.MainWindow;

/**
 * 인터페이스 검증 시스템 진입점.
 * 실행: java -cp out Main
 * 빌드: build.bat 실행 → dist\인터페이스검증시스템.exe
 */
public class Main {
    public static void main(String[] args) {
        // Windows 네이티브 Look & Feel 적용
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Swing 은 반드시 EDT 에서 실행
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
