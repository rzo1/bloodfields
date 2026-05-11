package com.github.rzo1.bloodfields.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MainMenuRenderer extends VBox {

    private final Label title = new Label("BLOODFIELD");
    private final Button campaignButton = new Button("CAMPAIGN");
    private final Button skirmishButton = new Button("SKIRMISH (vs Bot)");
    private final Button versusButton = new Button("VERSUS (2 players, hot-seat)");
    private final TextField codeField = new TextField();
    private final Button loadButton = new Button("Load");
    private final Label codeError = new Label("");
    private final Label hint = new Label("ENTER / SPACE start campaign  —  ESC quits");
    private final CheckBox colourBlindToggle = new CheckBox("Colour-blind palette");
    private final Label versionLabel = new Label(BuildInfo.displayString());
    private final HBox updateBanner = new HBox(10);
    private final Label updateLabel = new Label();
    private final Hyperlink updateDownloadLink = new Hyperlink("Download");
    private final Button updateDismissButton = new Button("Dismiss");

    private Runnable onCampaign;
    private Runnable onSkirmish;
    private Runnable onVersus;
    private IntConsumer onLoadLevel;
    private Consumer<String> onUpdateOpenUrl;

    public MainMenuRenderer() {
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(40));
        setStyle(Theme.optInWrapStyle());

        title.setFont(Font.font("Georgia", FontWeight.BOLD, 64));
        title.setStyle(Theme.menuTitleStyle());

        configureUpdateBanner();

        styleMenuButton(campaignButton);
        campaignButton.setOnAction(e -> {
            if (onCampaign != null) onCampaign.run();
        });

        styleMenuButton(skirmishButton);
        skirmishButton.setOnAction(e -> {
            if (onSkirmish != null) onSkirmish.run();
        });

        styleMenuButton(versusButton);
        versusButton.setOnAction(e -> {
            if (onVersus != null) onVersus.run();
        });

        Label codeLabel = new Label("Level Code:");
        codeLabel.setStyle(Theme.labelTitleStyle());

        codeField.setPromptText("e.g. WEDGE");
        codeField.setPrefWidth(160);
        codeField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                tryLoad();
            }
        });

        loadButton.getStyleClass().add(Theme.BUTTON_CLASS);
        loadButton.setOnAction(e -> tryLoad());

        HBox codeRow = new HBox(8, codeLabel, codeField, loadButton);
        codeRow.setAlignment(Pos.CENTER);

        codeError.setStyle("-fx-text-fill: " + Theme.TEXT_ACCENT + "; -fx-font-family: 'Georgia','Serif'; -fx-font-weight: bold;");
        codeError.setVisible(false);
        codeError.setManaged(false);

        hint.setStyle(Theme.labelDimStyle());

        colourBlindToggle.setSelected(AccessibilitySettings.get().colourBlindPalette());
        colourBlindToggle.setFont(Font.font("Georgia", FontWeight.NORMAL, 12));
        colourBlindToggle.setStyle("-fx-text-fill: " + Theme.TEXT_DIM + ";");
        colourBlindToggle.selectedProperty().addListener((obs, was, now) -> {
            AccessibilitySettings.get().setColourBlindPalette(Boolean.TRUE.equals(now));
            requestLayout();
        });

        versionLabel.setFont(Font.font("Georgia", FontWeight.NORMAL, 10));
        versionLabel.setStyle(Theme.labelDimStyle());
        versionLabel.setMaxWidth(Double.MAX_VALUE);
        versionLabel.setAlignment(Pos.BOTTOM_RIGHT);
        VBox.setMargin(versionLabel, new Insets(20, 0, 0, 0));

        getChildren().addAll(title, updateBanner, campaignButton, skirmishButton, versusButton, codeRow, codeError, hint, colourBlindToggle, versionLabel);
    }

    private void configureUpdateBanner() {
        updateLabel.setStyle(
                "-fx-text-fill: " + Theme.TEXT_PRIMARY + ";"
                        + "-fx-font-family: 'Georgia','Serif';"
                        + "-fx-font-size: 13px;"
                        + "-fx-font-weight: bold;");
        updateDownloadLink.setStyle(
                "-fx-text-fill: " + Theme.TEXT_ACCENT + ";"
                        + "-fx-font-family: 'Georgia','Serif';"
                        + "-fx-font-size: 13px;"
                        + "-fx-font-weight: bold;");
        updateDismissButton.getStyleClass().add(Theme.BUTTON_CLASS);
        updateDismissButton.setFont(Font.font("Georgia", FontWeight.NORMAL, 11));
        updateBanner.setAlignment(Pos.CENTER_LEFT);
        updateBanner.setPadding(new Insets(8, 12, 8, 12));
        updateBanner.setStyle(
                "-fx-background-color: rgba(40, 12, 10, 0.9);"
                        + "-fx-border-color: " + Theme.TEXT_ACCENT + " transparent transparent transparent;"
                        + "-fx-border-width: 0 0 0 4;"
                        + "-fx-border-style: solid;"
                        + "-fx-border-color: transparent transparent transparent " + Theme.TEXT_ACCENT + ";");
        updateBanner.getChildren().addAll(updateLabel, updateDownloadLink, updateDismissButton);
        updateBanner.setVisible(false);
        updateBanner.setManaged(false);
    }

    public void showUpdateBanner(String tag, String url, Runnable onDismiss) {
        updateLabel.setText("🩸 NEW VERSION AVAILABLE: " + tag);
        updateDownloadLink.setOnAction(e -> {
            if (onUpdateOpenUrl != null && url != null) {
                onUpdateOpenUrl.accept(url);
            }
        });
        updateDismissButton.setOnAction(e -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
            hideUpdateBanner();
        });
        updateBanner.setVisible(true);
        updateBanner.setManaged(true);
    }

    public void hideUpdateBanner() {
        updateBanner.setVisible(false);
        updateBanner.setManaged(false);
    }

    public void setOnUpdateOpenUrl(Consumer<String> handler) {
        this.onUpdateOpenUrl = handler;
    }

    HBox updateBanner() { return updateBanner; }

    private static void styleMenuButton(Button b) {
        b.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        b.setPrefWidth(360);
        b.setPrefHeight(48);
        b.getStyleClass().add(Theme.BUTTON_CLASS);
    }

    public void setOnCampaign(Runnable r) { this.onCampaign = r; }
    public void setOnSkirmish(Runnable r) { this.onSkirmish = r; }
    public void setOnVersus(Runnable r) { this.onVersus = r; }
    public void setOnLoadLevel(IntConsumer c) { this.onLoadLevel = c; }

    public Button campaignButton() { return campaignButton; }
    public Button skirmishButton() { return skirmishButton; }
    public Button versusButton() { return versusButton; }
    public CheckBox colourBlindToggle() { return colourBlindToggle; }

    private void tryLoad() {
        String code = codeField.getText();
        int idx = LevelCodes.indexFor(code);
        if (idx < 0) {
            codeError.setText("Unknown code");
            codeError.setVisible(true);
            codeError.setManaged(true);
            return;
        }
        codeError.setVisible(false);
        codeError.setManaged(false);
        if (onLoadLevel != null) {
            onLoadLevel.accept(idx);
        }
    }
}
