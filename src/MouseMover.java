import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.URL;

public class MouseMover {
    public static void main(String[] args) throws IOException {
    final Mover mover = new Mover(45000, true, 30);

    Thread thread = new Thread(mover);
		mover.setStartStop(true);
    //CreateSystemTrayIcon();
		thread.start();
    }

    private static void CreateSystemTrayIcon() throws IOException {
        //Check the SystemTray is supported
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();
        URL url = MouseMover.class.getResource("/ico.bmp");

        //Use it to get the image
        Image image= ImageIO.read(MouseMover.class.getResource("/ico.bmp"));
        Image img = Toolkit.getDefaultToolkit().getImage(url);

        final TrayIcon trayIcon = new TrayIcon(img, "UanMover Running", popup);
        final SystemTray tray = SystemTray.getSystemTray();

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }
}
