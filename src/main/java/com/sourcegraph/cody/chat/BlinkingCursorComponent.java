package com.sourcegraph.cody.chat;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.JPanel;
import javax.swing.Timer;

public class BlinkingCursorComponent extends JPanel {
  private boolean showCursor;
  private Timer timer;

  public static BlinkingCursorComponent instance = new BlinkingCursorComponent();

  private BlinkingCursorComponent() {
    this.showCursor = true;
    this.timer =
        new Timer(
            500,
            e -> {
              showCursor = !showCursor;
              repaint();
            });
    this.timer.start();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (showCursor) {
      g.setFont(new Font("Monospaced", Font.PLAIN, 12));
      g.drawString("█", 10, 20);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(30, 30);
  }
}
