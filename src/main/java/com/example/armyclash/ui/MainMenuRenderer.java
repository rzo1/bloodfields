package com.example.armyclash.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

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
    private final Label versionLabel = new Label(BuildInfo.displayString());

    private Runnable onCampaign;
    private Runnable onSkirmish;
    private Runnable onVersus;
    private IntConsumer onLoadLevel;

    public MainMenuRenderer() {
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(40));
        setStyle("-fx-background-color: linear-gradient(to bottom, #0d1a0d, #1f3a1f);");

        title.setFont(Font.font("System", FontWeight.BOLD, 56));
        title.setStyle("-fx-text-fill: white;");

        campaignButton.setFont(Font.font("System", FontWeight.BOLD, 18));
        campaignButton.setPrefWidth(320);
        campaignButton.setStyle("-fx-background-color: #2a4d2a; -fx-text-fill: white;");
        campaignButton.setOnAction(e -> {
            if (onCampaign != null) onCampaign.run();
        });

        skirmishButton.setFont(Font.font("System", FontWeight.BOLD, 18));
        skirmishButton.setPrefWidth(320);
        skirmishButton.setStyle("-fx-background-color: #2a3a4d; -fx-text-fill: white;");
        skirmishButton.setOnAction(e -> {
            if (onSkirmish != null) onSkirmish.run();
        });

        versusButton.setFont(Font.font("System", FontWeight.BOLD, 18));
        versusButton.setPrefWidth(320);
        versusButton.setStyle("-fx-background-color: #4d2a2a; -fx-text-fill: white;");
        versusButton.setOnAction(e -> {
            if (onVersus != null) onVersus.run();
        });

        Label codeLabel = new Label("Level Code:");
        codeLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        codeField.setPromptText("e.g. WEDGE");
        codeField.setPrefWidth(160);
        codeField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                tryLoad();
            }
        });

        loadButton.setOnAction(e -> tryLoad());

        HBox codeRow = new HBox(8, codeLabel, codeField, loadButton);
        codeRow.setAlignment(Pos.CENTER);

        codeError.setStyle("-fx-text-fill: #ff8080;");
        codeError.setVisible(false);
        codeError.setManaged(false);

        hint.setStyle("-fx-text-fill: #aaaaaa;");

        versionLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        versionLabel.setStyle("-fx-text-fill: #888888;");
        versionLabel.setMaxWidth(Double.MAX_VALUE);
        versionLabel.setAlignment(Pos.BOTTOM_RIGHT);
        VBox.setMargin(versionLabel, new Insets(20, 0, 0, 0));

        getChildren().addAll(title, campaignButton, skirmishButton, versusButton, codeRow, codeError, hint, versionLabel);
    }

    public void setOnCampaign(Runnable r) { this.onCampaign = r; }
    public void setOnSkirmish(Runnable r) { this.onSkirmish = r; }
    public void setOnVersus(Runnable r) { this.onVersus = r; }
    public void setOnLoadLevel(IntConsumer c) { this.onLoadLevel = c; }

    public Button campaignButton() { return campaignButton; }
    public Button skirmishButton() { return skirmishButton; }
    public Button versusButton() { return versusButton; }

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
