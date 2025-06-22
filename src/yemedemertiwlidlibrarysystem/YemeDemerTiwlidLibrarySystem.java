package yemedemertiwlidlibrarysystem;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public class YemeDemerTiwlidLibrarySystem extends Application {

    // Database configuration
    private static final String DB_URL = "jdbc:mysql://localhost:3307/yeme_demer_tiwlid_library_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

// UI Colors
    private static final String LIGHT_BG = "#f5f5f5";
    private static final String LIGHT_CARD = "#ffffff";
    private static final String LIGHT_HOVER = "#f0f0f0";
    private static final String LIGHT_TEXT = "#333333";
    private static final String LIGHT_SECONDARY = "#e6e6e6";

    private static final String DARK_BG = "#162843";
    private static final String DARK_CARD = "#3A4D6E";
    private static final String DARK_HOVER = "#4A5D7E";
    private static final String DARK_TEXT = "#e0e0e0";
    private static final String DARK_SECONDARY = "#2D304A";

    // Application state
    private Connection connection;
    private User currentUser;
    private boolean darkMode = false;

    // Secret code for librarian registration
    private static final String SECRET_CODE = "YDT-library-mgmt-code";

    // UI Components
    private Stage primaryStage;
    private StackPane root;
    private BorderPane mainLayout;
    private VBox sidebar;
    private Label titleLabel;
    private FlowPane booksFlowPane;
    private ComboBox<String> searchTypeCombo;
    private TextField searchField;
    private static final int BOOKS_PER_PAGE = 50;
    private int currentPage = 0;
    private final Map<String, Node> bookCardCache = new HashMap<>();
    private Timeline searchTimeline;

    // Animation constants
    private static final Duration HOVER_ANIM_DURATION = Duration.millis(150);
    private static final Duration PRESS_ANIM_DURATION = Duration.millis(100);
    private static final Duration CARD_HOVER_ANIM_DURATION = Duration.millis(200);
    private static final Duration TRANSITION_DURATION = Duration.millis(300);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeDatabase();
        initializeUI();
        showLoginScreen();

        // Add fade-in animation for the initial screen
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.5), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connection successful!");
            createTables();
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found: " + e.getMessage());
            showAlert("Database Error", "MySQL JDBC driver not found: " + e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("Could not connect to MySQL: " + e.getMessage());
            showAlert("Database Error", "Could not connect to MySQL: " + e.getMessage());
            System.exit(1);
        }
    }

    private void createTables() throws SQLException {
        // Users table
        String usersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "full_name VARCHAR(100) NOT NULL,"
                + "username VARCHAR(50) UNIQUE NOT NULL,"
                + "password VARCHAR(255) NOT NULL,"
                + "is_admin BOOLEAN DEFAULT FALSE)";

        // Books table
        String booksTable = "CREATE TABLE IF NOT EXISTS books ("
                + "isbn VARCHAR(20) PRIMARY KEY,"
                + "title VARCHAR(255) NOT NULL,"
                + "author VARCHAR(100) NOT NULL,"
                + "genre VARCHAR(50) NOT NULL,"
                + "shelf_number VARCHAR(20) NOT NULL,"
                + "status VARCHAR(20) NOT NULL,"
                + "quantity INT NOT NULL,"
                + "cover_image LONGBLOB)";

        // Loans table
        String loansTable = "CREATE TABLE IF NOT EXISTS loans ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "isbn VARCHAR(20) NOT NULL,"
                + "borrower_id VARCHAR(50) NOT NULL,"
                + "borrower_name VARCHAR(100) NOT NULL,"
                + "loan_date DATETIME NOT NULL,"
                + "return_date DATETIME NOT NULL,"
                + "returned BOOLEAN DEFAULT FALSE,"
                + "FOREIGN KEY (isbn) REFERENCES books(isbn))";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(booksTable);
            stmt.execute(loansTable);

            if (isTableEmpty("users")) {
                insertSampleUsers();
            }
        }
    }

    private boolean isTableEmpty(String tableName) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private void insertSampleUsers() throws SQLException {
        String sql = "INSERT INTO users (full_name, username, password, is_admin) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Admin user
            pstmt.setString(1, "Admin User");
            pstmt.setString(2, "admin");
            pstmt.setString(3, hashPassword("admin123"));
            pstmt.setBoolean(4, true);
            pstmt.executeUpdate();

            // Librarian user
            pstmt.setString(1, "Library Staff");
            pstmt.setString(2, "librarian");
            pstmt.setString(3, hashPassword("lib123"));
            pstmt.setBoolean(4, false);
            pstmt.executeUpdate();
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    private void initializeUI() {
        root = new StackPane();
        root.setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

        Scene scene = new Scene(root, 1280, 800);
        primaryStage.setTitle("Yemedemer Tiwlid Library Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showLoginScreen() {
        VBox loginBox = new VBox(20);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(40));
        loginBox.setMaxWidth(400);
        loginBox.setStyle("-fx-background-color: " + (darkMode ? DARK_CARD : LIGHT_CARD) + "; "
                + "-fx-background-radius: 10; -fx-padding: 30; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);");

        // Add scale animation on appearance
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(500), loginBox);
        scaleIn.setFromX(0.9);
        scaleIn.setFromY(0.9);
        scaleIn.setToX(1);
        scaleIn.setToY(1);

        Label loginTitle = new Label("Yemedemer Tiwlid Library Login");
        loginTitle.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        loginTitle.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.web(LIGHT_TEXT));

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Username");
        styleTextField(usernameInput);

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Password");
        styleTextField(passwordInput);

        Button loginButton = new Button("Login");
        styleButton(loginButton, "#2a9df4", "#3aa8ff", "#1a7bc8");
        loginButton.setMaxWidth(300);

        loginButton.setOnAction(e -> {
            animateButtonPress(loginButton, () -> {
                String username = usernameInput.getText();
                String password = passwordInput.getText();

                if (username.isEmpty() || password.isEmpty()) {
                    showAlert("Error", "Please enter both username and password");
                    shakeLoginBox(loginBox);
                    return;
                }

                try {
                    if (authenticateUser(username, password)) {
                        if (currentUser != null) {
                            showMainDashboard();
                        }
                    }
                    // Error message is now handled in authenticateUser
                } catch (Exception ex) {
                    showAlert("Error", "Authentication failed: " + ex.getMessage());
                    shakeLoginBox(loginBox);
                }
            });
        });

        Button signUpButton = new Button("Librarian Sign Up");
        styleTextButton(signUpButton, "#2a9df4");
        signUpButton.setOnAction(e -> showSignUpScreen());

        loginBox.getChildren().addAll(loginTitle, usernameInput, passwordInput, loginButton, signUpButton);
        root.getChildren().clear();
        root.getChildren().add(loginBox);

        // Play animation
        scaleIn.play();
    }

    private void shakeLoginBox(VBox loginBox) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), loginBox);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);

        // Also add slight vertical shake
        TranslateTransition shakeY = new TranslateTransition(Duration.millis(50), loginBox);
        shakeY.setFromY(0);
        shakeY.setByY(3);
        shakeY.setCycleCount(6);
        shakeY.setAutoReverse(true);

        ParallelTransition combo = new ParallelTransition(shake, shakeY);
        combo.play();
    }

    private void styleTextField(TextField textField) {
        textField.setMaxWidth(300);
        textField.setStyle("-fx-background-color: white; -fx-border-color: #ccc; "
                + "-fx-border-radius: 5; -fx-padding: 8; -fx-font-size: 14;");

        // Add focus animation
        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                textField.setStyle("-fx-background-color: white; -fx-border-color: #2a9df4; "
                        + "-fx-border-radius: 5; -fx-padding: 8; -fx-font-size: 14;");
            } else {
                textField.setStyle("-fx-background-color: white; -fx-border-color: #ccc; "
                        + "-fx-border-radius: 5; -fx-padding: 8; -fx-font-size: 14;");
            }
        });
    }

    private Image createPlaceholderImage() {
        Canvas canvas = new Canvas(200, 150);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Use card color as background
        gc.setFill(darkMode ? Color.web(DARK_CARD) : Color.WHITE);
        gc.fillRect(0, 0, 200, 150);

        // Use appropriate text color
        gc.setFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        gc.fillText("No Cover", 70, 75);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private void setBookCoverImage(ImageView imageView, byte[] imageData) {
        try {
            if (imageData != null && imageData.length > 0) {
                Image image = new Image(new ByteArrayInputStream(imageData));
                imageView.setImage(image);
            } else {
                imageView.setImage(createPlaceholderImage());
            }
        } catch (Exception e) {
            imageView.setImage(createPlaceholderImage());
        }
    }

    private void styleButton(Button button, String baseColor, String hoverColor, String pressedColor) {
        // Base style with gradient
        String baseStyle = "-fx-background-color: linear-gradient(to bottom, " + baseColor + ", " + darkenColor(baseColor, 0.2) + "); "
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; "
                + "-fx-padding: 8 15 8 15; -fx-cursor: hand; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 1);";

        // Hover style
        String hoverStyle = "-fx-background-color: linear-gradient(to bottom, " + hoverColor + ", " + darkenColor(hoverColor, 0.2) + "); "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0, 0, 2);";

        // Pressed style
        String pressedStyle = "-fx-background-color: linear-gradient(to bottom, " + darkenColor(pressedColor, 0.1) + ", " + pressedColor + "); "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1);";

        button.setStyle(baseStyle);

        // Hover animation
        final ScaleTransition hoverScale = new ScaleTransition(HOVER_ANIM_DURATION, button);
        button.setOnMouseEntered(e -> {
            button.setStyle(baseStyle + hoverStyle);
            hoverScale.setToX(1.03);
            hoverScale.setToY(1.03);
            hoverScale.play();
        });

        button.setOnMouseExited(e -> {
            button.setStyle(baseStyle);
            hoverScale.setToX(1);
            hoverScale.setToY(1);
            hoverScale.play();
        });

        // Press animation
        button.setOnMousePressed(e -> {
            button.setStyle(baseStyle + pressedStyle);
            ScaleTransition pressScale = new ScaleTransition(PRESS_ANIM_DURATION, button);
            pressScale.setToX(0.97);
            pressScale.setToY(0.97);
            pressScale.play();
        });

        button.setOnMouseReleased(e -> {
            button.setStyle(baseStyle + hoverStyle);
            ScaleTransition releaseScale = new ScaleTransition(PRESS_ANIM_DURATION, button);
            releaseScale.setToX(1.03);
            releaseScale.setToY(1.03);
            releaseScale.play();
        });
    }

    private void styleTextButton(Button button, String color) {
        String baseStyle = "-fx-background-color: transparent; "
                + "-fx-text-fill: " + color + "; "
                + "-fx-underline: true; "
                + "-fx-cursor: hand; "
                + "-fx-font-size: 14;";

        String hoverStyle = "-fx-background-color: rgba(" + hexToRgb(color) + ",0.1); "
                + "-fx-text-fill: " + darkenColor(color, 0.2) + ";";

        button.setStyle(baseStyle);

        button.setOnMouseEntered(e -> button.setStyle(baseStyle + hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
    }

    private String darkenColor(String hexColor, double factor) {
        Color color = Color.web(hexColor);
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255 * (1 - factor)),
                (int) (color.getGreen() * 255 * (1 - factor)),
                (int) (color.getBlue() * 255 * (1 - factor)));
    }

    private String hexToRgb(String hexColor) {
        Color color = Color.web(hexColor);
        return (int) (color.getRed() * 255) + ","
                + (int) (color.getGreen() * 255) + ","
                + (int) (color.getBlue() * 255);
    }

    private void animateButtonPress(Button button, Runnable action) {
        ScaleTransition press = new ScaleTransition(Duration.millis(100), button);
        press.setToX(0.95);
        press.setToY(0.95);

        ScaleTransition release = new ScaleTransition(Duration.millis(100), button);
        release.setToX(1);
        release.setToY(1);

        press.setOnFinished(e -> {
            release.play();
            action.run();
        });
        press.play();
    }

    private boolean authenticateUser(String username, String password) throws SQLException {
        validateConnection();

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashPassword(password));

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                currentUser = new User(
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getBoolean("is_admin")
                );
                return true;
            } else {
                Platform.runLater(() -> {
                    showAlert("Login Failed", "Invalid username or password");
                    shakeLoginBox((VBox) root.getChildren().get(0));
                    // Clear password field
                    if (root.getChildren().get(0) instanceof VBox) {
                        VBox loginBox = (VBox) root.getChildren().get(0);
                        for (Node node : loginBox.getChildren()) {
                            if (node instanceof PasswordField) {
                                ((PasswordField) node).clear();
                            }
                        }
                    }
                });
                return false;
            }
        }
    }
    

    private void validateConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (SQLException e) {
                showAlert("Database Error", "Could not connect to database: " + e.getMessage());
                throw e;
            }
        }
    }

    private void showSignUpScreen() {
        VBox signUpBox = new VBox(15);
        signUpBox.setAlignment(Pos.CENTER);
        signUpBox.setPadding(new Insets(30));
        signUpBox.setMaxWidth(500);
        signUpBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 30; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);");

        // Add animation
        FadeTransition fadeIn = new FadeTransition(TRANSITION_DURATION, signUpBox);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        Label signUpTitle = new Label("Librarian Sign Up");
        signUpTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        signUpTitle.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);

        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full Name");
        styleTextField(fullNameField);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        styleTextField(usernameField);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        styleTextField(passwordField);

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm Password");
        styleTextField(confirmPasswordField);

        PasswordField secretCodeField = new PasswordField();
        secretCodeField.setPromptText("Enter Secret Code");
        styleTextField(secretCodeField);

        Button signUpButton = new Button("Sign Up");
        styleButton(signUpButton, "#2a9df4", "#3aa8ff", "#1a7bc8");
        signUpButton.setOnAction(e -> {
            try {
                String fullName = fullNameField.getText();
                String username = usernameField.getText();
                String password = passwordField.getText();
                String confirmPassword = confirmPasswordField.getText();
                String secretCode = secretCodeField.getText();

                if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()
                        || confirmPassword.isEmpty() || secretCode.isEmpty()) {
                    showAlert("Error", "All fields are required");
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    showAlert("Error", "Passwords do not match");
                    return;
                }

                if (!secretCode.equals(SECRET_CODE)) {
                    showAlert("Error", "Invalid secret code. Please contact the system administrator.");
                    return;
                }

                if (usernameExists(username)) {
                    showAlert("Error", "Username already exists");
                    return;
                }

                registerUser(fullName, username, password);
                showAlert("Success", "Account created successfully!");
                showLoginScreen();

            } catch (Exception ex) {
                showAlert("Error", "Registration failed: " + ex.getMessage());
            }
        });

        Button backButton = new Button("Back to Login");
        styleTextButton(backButton, "#2a9df4");
        backButton.setOnAction(e -> showLoginScreen());

        signUpBox.getChildren().addAll(
                signUpTitle, fullNameField, usernameField, passwordField,
                confirmPasswordField, secretCodeField, signUpButton, backButton
        );

        root.getChildren().clear();
        root.getChildren().add(signUpBox);

        // Play animation
        fadeIn.play();
    }

    private boolean usernameExists(String username) throws SQLException {
        validateConnection();

        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void registerUser(String fullName, String username, String password) throws SQLException {
        validateConnection();

        String sql = "INSERT INTO users (full_name, username, password) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fullName);
            pstmt.setString(2, username);
            pstmt.setString(3, hashPassword(password));
            pstmt.executeUpdate();
        }
    }

    private void showMainDashboard() {
        try {
            // Clear existing content
            root.getChildren().clear();

            // Initialize main layout
            mainLayout = new BorderPane();
            updateTheme(); // Apply current theme

            // Create components
            createHeader();
            createSidebar();
            showDashboardContent();
            scheduleOverdueCheck();

            // Add to root
            root.getChildren().add(mainLayout);

            // Animation
            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), mainLayout);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();

        } catch (Exception e) {
            System.err.println("Error showing main dashboard: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "Failed to load dashboard: " + e.getMessage());
        }
    }

    private void createHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: " + (darkMode ? DARK_CARD : "#2a9df4") + ";");
        header.setAlignment(Pos.CENTER_LEFT);

        titleLabel = new Label("Dashboard - Yemedemer Tiwlid Library");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);
        System.out.println("Header title set: " + titleLabel.getText());

        HBox rightControls = new HBox(10);
        rightControls.setAlignment(Pos.CENTER_RIGHT);

        Button themeButton = new Button(darkMode ? "☀️ Light" : "🌙 Dark");
        styleButton(themeButton, "#6c757d", "#5a6268", "#4e555b");
        themeButton.setOnAction(e -> {
            // Add scale animation
            ScaleTransition st = new ScaleTransition(Duration.millis(200), themeButton);
            st.setFromX(1);
            st.setFromY(1);
            st.setToX(0.9);
            st.setToY(0.9);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();

            // Toggle dark mode after animation
            st.setOnFinished(ev -> {
                darkMode = !darkMode;
                themeButton.setText(darkMode ? "☀️ Light" : "🌙 Dark");
                updateTheme(); // Use our new comprehensive update method
            });
        });

        Label userLabel = new Label("Logged in as: " + currentUser.getFullName());
        userLabel.setTextFill(Color.WHITE);

        Button logoutButton = new Button("Logout");
        styleButton(logoutButton, "#dc3545", "#c82333", "#bd2130");
        logoutButton.setOnAction(e -> showLoginScreen());

        rightControls.getChildren().addAll(userLabel, themeButton, logoutButton);
        HBox.setHgrow(rightControls, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, rightControls);
        mainLayout.setTop(header);
    }

    private void createSidebar() {
        sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("-fx-background-color: " + (darkMode ? DARK_SECONDARY : LIGHT_SECONDARY) + ";");
        sidebar.setPrefWidth(200);

        Label menuLabel = new Label("Menu");
        menuLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        menuLabel.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.web(LIGHT_TEXT));

        Button dashboardBtn = createMenuButton("Dashboard", "📊");
        dashboardBtn.setOnAction(e -> showDashboardContent());

        Button booksBtn = createMenuButton("Books", "📚");
        booksBtn.setOnAction(e -> showBooksContent());

        Button loansBtn = createMenuButton("Loans", "📖");
        loansBtn.setOnAction(e -> showLoansContent());

        Button searchBtn = createMenuButton("Search", "🔍");
        searchBtn.setOnAction(e -> showSearchContent());

        Button analysisBtn = createMenuButton("Analysis", "📈");
        analysisBtn.setOnAction(e -> showAnalysisContent());

        Button developersBtn = createMenuButton("Developers", "👨‍💻");
        developersBtn.setOnAction(e -> showDevelopersWindow());

        if (currentUser.isAdmin()) {
            Button settingsBtn = createMenuButton("Settings", "⚙️");
            settingsBtn.setOnAction(e -> showSettingsContent());
            sidebar.getChildren().addAll(menuLabel, dashboardBtn, booksBtn, loansBtn,
                    searchBtn, analysisBtn, settingsBtn, developersBtn);
        } else {
            sidebar.getChildren().addAll(menuLabel, dashboardBtn, booksBtn, loansBtn,
                    searchBtn, analysisBtn, developersBtn);
        }
        mainLayout.setLeft(sidebar);
    }

    private Button createMenuButton(String text, String emoji) {
        Button button = new Button(emoji + " " + text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.BASELINE_LEFT);

        // Base style
        String baseStyle = "-fx-background-color: transparent; "
                + "-fx-text-fill: " + (darkMode ? DARK_TEXT : "black") + "; "
                + "-fx-font-size: 14px; "
                + "-fx-padding: 10 15 10 15; "
                + "-fx-background-radius: 5; "
                + "-fx-border-radius: 5; "
                + "-fx-cursor: hand;";

        // Hover style
        String hoverStyle = "-fx-background-color: " + (darkMode ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.05)") + "; "
                + "-fx-text-fill: " + (darkMode ? "white" : "black") + ";";

    button.setOnAction(e -> {
        switch (text) {
            case "Books":
                refreshBooksContent(); // Force refresh when Books button is clicked
                showBooksContent();
                break;
            case "Dashboard":
                showDashboardContent();
                break;
            case "Loans":
                showLoansContent();
                break;
            // ... other cases ...
        }
    });
        button.setStyle(baseStyle);

        button.setOnMouseEntered(e -> button.setStyle(baseStyle + hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));

        return button;
    }

    private void showDashboardContent() {
        titleLabel.setText("Dashboard - Yemedemer Tiwlid Library");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        // Summary cards
        HBox summaryCards = new HBox(15);
        summaryCards.setAlignment(Pos.CENTER);

        int totalBooks = getTotalBooksCount();
        int onLoan = getOnLoanCount();
        int overdue = getOverdueCount();

        summaryCards.getChildren().addAll(
                createSummaryCard("Total Books", String.valueOf(totalBooks), "#2a9df4"),
                createSummaryCard("On Loan", String.valueOf(onLoan), "#17a2b8"),
                createSummaryCard("Overdue", String.valueOf(overdue), "#dc3545")
        );

        // Recent activity
        Label activityLabel = new Label("Recent Activity");
        activityLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        activityLabel.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);

        ListView<String> activityList = new ListView<>();
        activityList.getItems().addAll(getRecentActivities());
        activityList.setPrefHeight(200);
        activityList.setStyle("-fx-control-inner-background: " + (darkMode ? DARK_CARD : "white") + ";");

        // Quick actions
        HBox quickActions = new HBox(15);
        quickActions.setAlignment(Pos.CENTER);

        Button addBookBtn = new Button("Add New Book");
        styleButton(addBookBtn, "#28a745", "#34ce57", "#218838");
        addBookBtn.setOnAction(e -> showAddBookDialog());

        Button viewLoansBtn = new Button("View All Loans");
        styleButton(viewLoansBtn, "#17a2b8", "#138496", "#117a8b");
        viewLoansBtn.setOnAction(e -> showLoansContent());

        quickActions.getChildren().addAll(addBookBtn, viewLoansBtn);

        content.getChildren().addAll(summaryCards, activityLabel, activityList, quickActions);
        mainLayout.setCenter(content);
    }

    private VBox createSummaryCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));

        // Use proper colors for both modes
        String cardColor = darkMode ? DARK_CARD : LIGHT_CARD;
        String hoverColor = darkMode ? DARK_HOVER : LIGHT_HOVER;
        String textColor = darkMode ? DARK_TEXT : LIGHT_TEXT;

        card.setStyle("-fx-background-color: " + cardColor
                + "; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
        card.setPrefWidth(200);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web(textColor));

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        valueLabel.setTextFill(Color.web(color));

        // Hover effects
        String cardStyle = card.getStyle();
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: " + hoverColor
                    + "; -fx-background-radius: 10; "
                    + "-fx-effect: dropshadow(three-pass-box, "
                    + (darkMode ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.2)")
                    + ", 10, 0, 0, 0);");
            card.setCursor(Cursor.HAND);

            ScaleTransition hoverScale = new ScaleTransition(CARD_HOVER_ANIM_DURATION, card);
            hoverScale.setToX(1.02);
            hoverScale.setToY(1.02);
            hoverScale.play();
        });

        card.setOnMouseExited(e -> {
            card.setStyle(cardStyle);
            ScaleTransition exitScale = new ScaleTransition(CARD_HOVER_ANIM_DURATION, card);
            exitScale.setToX(1);
            exitScale.setToY(1);
            exitScale.play();
        });

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private int getTotalBooksCount() {
        String sql = "SELECT SUM(quantity) FROM books";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            showAlert("Error", "Could not retrieve book count");
            return 0;
        }
    }

    private int getOnLoanCount() {
        String sql = "SELECT COUNT(*) FROM loans WHERE returned = 0";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            showAlert("Error", "Could not retrieve loan count");
            return 0;
        }
    }

    private int getOverdueCount() {
        String sql = "SELECT COUNT(*) FROM loans WHERE returned = 0 AND return_date < ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            showAlert("Error", "Could not retrieve overdue count");
            return 0;
        }
    }

    private List<String> getRecentActivities() {
        List<String> activities = new ArrayList<>();
        String sql = "SELECT b.title, l.borrower_name, l.loan_date "
                + "FROM loans l JOIN books b ON l.isbn = b.isbn "
                + "ORDER BY l.loan_date DESC LIMIT 5";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                activities.add(String.format("'%s' borrowed by %s on %s",
                        rs.getString("title"),
                        rs.getString("borrower_name"),
                        rs.getString("loan_date")));
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not retrieve recent activities");
        }

        if (activities.isEmpty()) {
            activities.add("No recent activities");
        }

        return activities;
    }

  private void showBooksContent() {
    titleLabel.setText("Book Management - Yeme Demer Tiwlid Library");

    VBox content = new VBox(20);
    content.setPadding(new Insets(20));

    // Add refresh button next to search
    HBox searchBox = new HBox(10);
    searchField = new TextField();
    searchField.setPromptText("Search books...");
    searchField.setPrefWidth(400);
    styleTextField(searchField);

    searchTypeCombo = new ComboBox<>();
    searchTypeCombo.getItems().addAll("Title", "Author", "ISBN", "Genre", "Shelf", "Status");
    searchTypeCombo.setValue("Title");
    styleComboBox(searchTypeCombo);

    Button refreshButton = new Button("Refresh");
    styleButton(refreshButton, "#17a2b8", "#138496", "#117a8b");
    refreshButton.setOnAction(e -> refreshBooksContent());

    Button addBookButton = new Button("+ Add Book");
    styleButton(addBookButton, "#28a745", "#34ce57", "#218838");
    addBookButton.setOnAction(e -> showAddBookDialog());

    searchBox.getChildren().addAll(searchField, searchTypeCombo, refreshButton, addBookButton);

    // Books display
    booksFlowPane = new FlowPane();
    booksFlowPane.setPadding(new Insets(15));
    booksFlowPane.setHgap(20);
    booksFlowPane.setVgap(20);
    booksFlowPane.setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

    ScrollPane scrollPane = createBookScrollPane();
    scrollPane.setStyle("-fx-background: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

    content.getChildren().addAll(searchBox, scrollPane);
    mainLayout.setCenter(content);

    // Load books with reset pagination
    currentPage = 0;
    booksFlowPane.getChildren().clear();
    bookCardCache.clear();
    loadBooksFromDatabase();
}
  
  private void refreshBooksContent() {
    // Reset pagination
    currentPage = 0;
    
    // Clear existing books and cache
    booksFlowPane.getChildren().clear();
    bookCardCache.clear();
    
    // Reload books
    loadBooksFromDatabase();
    
    // Show loading indicator
    showLoadingIndicator(true);
    
    // If there's a search term, reapply it
    if (searchField != null && !searchField.getText().isEmpty()) {
        performSearch(searchField.getText(), searchTypeCombo.getValue());
    }
}
  
  
    private void styleComboBox(ComboBox<String> comboBox) {
        comboBox.setStyle("-fx-background-color: white; -fx-border-color: #ccc; "
                + "-fx-border-radius: 5; -fx-padding: 5; -fx-font-size: 14;");

        comboBox.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };

            cell.setOnMouseEntered(e -> cell.setStyle("-fx-background-color: #f0f0f0;"));
            cell.setOnMouseExited(e -> cell.setStyle(""));
            return cell;
        });
    }

    private ScrollPane createBookScrollPane() {
        ScrollPane scrollPane = new ScrollPane(booksFlowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Load more when scrolling near bottom
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0.9) { // 90% scrolled
                currentPage++;
                loadBooksFromDatabase();
            }
        });

        return scrollPane;
    }

    private void loadBooksFromDatabase() {
    showLoadingIndicator(true);

    Task<List<Book>> loadTask = new Task<>() {
        @Override
        protected List<Book> call() throws Exception {
            List<Book> batch = new ArrayList<>();
            String sql = "SELECT * FROM books LIMIT ? OFFSET ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, BOOKS_PER_PAGE);
                pstmt.setInt(2, currentPage * BOOKS_PER_PAGE);

                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Book book = new Book(
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getString("isbn"),
                            rs.getString("genre"),
                            rs.getString("shelf_number"),
                            rs.getString("status"),
                            rs.getInt("quantity"),
                            rs.getBytes("cover_image")
                    );
                    batch.add(book);
                }
            }
            return batch;
        }
    };

    loadTask.setOnSucceeded(e -> {
        List<Book> books = loadTask.getValue();
        Platform.runLater(() -> {
            for (Book book : books) {
                addBookCardWithAnimation(book);
            }
            showLoadingIndicator(false);
        });
    });

    loadTask.setOnFailed(e -> {
        Platform.runLater(() -> {
            showLoadingIndicator(false);
            showAlert("Error", "Failed to load books: " + loadTask.getException().getMessage());
        });
    });

    new Thread(loadTask).start();
}

    private void addBookCardWithAnimation(Book book) {
        Node card = createBookCard(book);
        booksFlowPane.getChildren().add(card);

        // Fade-in animation
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), card);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        // Scale animation
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), card);
        scaleIn.setFromX(0.95);
        scaleIn.setFromY(0.95);
        scaleIn.setToX(1);
        scaleIn.setToY(1);

        // Play animations in parallel
        ParallelTransition transition = new ParallelTransition(fadeIn, scaleIn);
        transition.play();
    }

    private int getAvailableCount(String isbn) {
        String sql = "SELECT quantity FROM books WHERE isbn = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int total = rs.getInt("quantity");

                // Get number of copies on loan (not returned)
                sql = "SELECT COUNT(*) FROM loans WHERE isbn = ? AND returned = 0";
                try (PreparedStatement pstmt2 = connection.prepareStatement(sql)) {
                    pstmt2.setString(1, isbn);
                    ResultSet rs2 = pstmt2.executeQuery();
                    int onLoan = rs2.next() ? rs2.getInt(1) : 0;
                    return total - onLoan;
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not check availability");
        }
        return 0;
    }

    private Node createBookCard(Book book) {
        if (bookCardCache.containsKey(book.getIsbn())) {
            return bookCardCache.get(book.getIsbn());
        }

        VBox card = new VBox(10);
        card.setPadding(new Insets(15));

        // Dynamic properties for theme
        StringProperty normalColor = new SimpleStringProperty();
        StringProperty hoverColor = new SimpleStringProperty();
        ObjectProperty<Color> textColor = new SimpleObjectProperty<>();
        updateCardColors(normalColor, hoverColor, textColor);

        // Bind card style
        card.setStyle("-fx-background-color: " + (darkMode ? DARK_CARD : "white")
                + "; -fx-background-radius: 10; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");

        card.setPrefWidth(250);

        // Book cover image
        ImageView coverImage = new ImageView();
        coverImage.setFitWidth(200);
        coverImage.setFitHeight(150);
        coverImage.setPreserveRatio(true);

        // Bind cover image
        book.coverImageProperty().addListener((obs, oldVal, newVal) -> {
            setBookCoverImage(coverImage, newVal);
        });
        setBookCoverImage(coverImage, book.getCoverImage());

        // Book details with bindings
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(book.titleProperty());
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setWrapText(true);
        titleLabel.textFillProperty().bind(textColor);

        Label authorLabel = new Label();
        authorLabel.textProperty().bind(Bindings.concat("by ", book.authorProperty()));
        authorLabel.setFont(Font.font("Arial", 12));
        authorLabel.textFillProperty().bind(textColor);

        Label detailsLabel = new Label();
        detailsLabel.textProperty().bind(Bindings.createStringBinding(()
                -> String.format("ISBN: %s\nGenre: %s\nShelf: %s\nStatus: %s\nAvailable: %d",
                        book.getIsbn(), book.getGenre(), book.getShelfNumber(),
                        book.getStatus(), getAvailableCount(book.getIsbn())),
                book.isbnProperty(), book.genreProperty(), book.shelfNumberProperty(),
                book.statusProperty()));
        detailsLabel.setFont(Font.font("Arial", 12));
        detailsLabel.textFillProperty().bind(textColor);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button loanButton = new Button("Loan");
        styleButton(loanButton, "#17a2b8", "#138496", "#117a8b");
        loanButton.setOnAction(e -> showLoanBookDialog(book));

        Button editButton = new Button("Edit");
        styleButton(editButton, "#ffc107", "#e0a800", "#d39e00");
        editButton.setOnAction(e -> showEditBookDialog(book));

        Button deleteButton = new Button("Delete");
        styleButton(deleteButton, "#dc3545", "#c82333", "#bd2130");
        deleteButton.setOnAction(e -> deleteBook(book));

        buttonBox.getChildren().addAll(loanButton, editButton, deleteButton);
        card.getChildren().addAll(coverImage, titleLabel, authorLabel, detailsLabel, buttonBox);

        // Hover effects
        card.hoverProperty().addListener((obs, wasHovered, isHovered) -> {
            if (isHovered) {
                card.setStyle("-fx-background-color: " + hoverColor.get()
                        + "; -fx-background-radius: 10; "
                        + "-fx-effect: dropshadow(three-pass-box, "
                        + (darkMode ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.2)")
                        + ", 10, 0, 0, 0);");
                card.setCursor(Cursor.HAND);
            } else {
                card.setStyle("-fx-background-color: " + normalColor.get()
                        + "; -fx-background-radius: 10; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 0);");
            }
        });

        // Add listener to update colors when darkMode changes
        primaryStage.getScene().getRoot().styleProperty().addListener((obs, oldVal, newVal) -> {
            updateCardColors(normalColor, hoverColor, textColor);
        });

        bookCardCache.put(book.getIsbn(), card);
        return card;
    }

    private void updateTheme() {
        // Update root background
        root.setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

        // Update main layout
        if (mainLayout != null) {
            mainLayout.setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

            // Update header
            Node header = mainLayout.getTop();
            if (header instanceof HBox) {
                header.setStyle("-fx-background-color: " + (darkMode ? DARK_CARD : "#2a9df4") + ";");
            }

            // Update sidebar
            Node sidebar = mainLayout.getLeft();
            if (sidebar instanceof VBox) {
                sidebar.setStyle("-fx-background-color: " + (darkMode ? DARK_SECONDARY : LIGHT_SECONDARY) + ";");
                updateSidebarColors((VBox) sidebar);
            }

            // Update center content
            updateCenterContent(mainLayout.getCenter());
        }

        if (titleLabel != null) {
            String currentTitle = titleLabel.getText();
            if (currentTitle.contains("Book") || currentTitle.contains("Search")) {
                showBooksContent();
            } else if (currentTitle.contains("Dashboard")) {
                showDashboardContent();
            } else if (currentTitle.contains("Loan")) {
                showLoansContent();
            }

            // Clear book card cache to force recreation with new colors
            bookCardCache.clear();

            // Refresh current view if it's book-related
            if (titleLabel != null && titleLabel.getText().contains("Book")) {
                showBooksContent();
            }
        }

    

    }

    private void updateSidebarColors(VBox sidebar) {
        for (Node node : sidebar.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if (button.getText().startsWith("📊") || button.getText().startsWith("📚")
                        || button.getText().startsWith("📖") || button.getText().startsWith("🔍")
                        || button.getText().startsWith("📈") || button.getText().startsWith("⚙️")
                        || button.getText().startsWith("👨‍💻")) {

                    String baseStyle = "-fx-background-color: transparent; "
                            + "-fx-text-fill: " + (darkMode ? DARK_TEXT : "black") + "; "
                            + "-fx-font-size: 14px; "
                            + "-fx-padding: 10 15 10 15; "
                            + "-fx-background-radius: 5; "
                            + "-fx-border-radius: 5; "
                            + "-fx-cursor: hand;";

                    String hoverStyle = "-fx-background-color: " + (darkMode ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.05)") + "; "
                            + "-fx-text-fill: " + (darkMode ? "white" : "black") + ";";

                    button.setStyle(baseStyle);
                    button.setOnMouseEntered(e -> button.setStyle(baseStyle + hoverStyle));
                    button.setOnMouseExited(e -> button.setStyle(baseStyle));
                }
            } else if (node instanceof Label) {
                ((Label) node).setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);
            }
        }
    }

    private void updateCenterContent(Node center) {
        if (center instanceof VBox) {
            VBox vbox = (VBox) center;
            for (Node node : vbox.getChildren()) {
                if (node instanceof HBox) {
                    // Update summary cards
                    for (Node card : ((HBox) node).getChildren()) {
                        if (card instanceof VBox) {
                            card.setStyle("-fx-background-color: " + (darkMode ? DARK_CARD : "white") + ";");
                            for (Node cardContent : ((VBox) card).getChildren()) {
                                if (cardContent instanceof Label) {
                                    Label label = (Label) cardContent;
                                    if (label.getFont().getStyle().contains("Bold") && label.getFont().getSize() == 24) {
                                        // This is the value label - keep its accent color
                                        continue;
                                    }
                                    label.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);
                                }
                            }
                        }
                    }
                } else if (node instanceof ListView) {
                    ((ListView<?>) node).setStyle("-fx-control-inner-background: " + (darkMode ? DARK_CARD : "white") + ";");
                } else if (node instanceof Label) {
                    ((Label) node).setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);
                }
            }
        } else if (center instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) center;
            scrollPane.setStyle("-fx-background: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");
            if (scrollPane.getContent() instanceof FlowPane) {
                // This is the books flow pane
                ((FlowPane) scrollPane.getContent()).setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");
            }
        }
    }

    private void updateCardColors(StringProperty normalColor, StringProperty hoverColor,
            ObjectProperty<Color> textColor) {
        normalColor.set(darkMode ? DARK_CARD : "white");
        hoverColor.set(darkMode ? DARK_HOVER : "#f0f0f0");
        textColor.set(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);
    }

    private void scheduleOverdueCheck() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.minutes(5), e -> {
                    Platform.runLater(() -> {
                        if (mainLayout != null && mainLayout.getCenter() instanceof VBox) {
                            VBox center = (VBox) mainLayout.getCenter();
                            if (center.getChildren().get(0) instanceof HBox) {
                                HBox summaryCards = (HBox) center.getChildren().get(0);
                                if (summaryCards.getChildren().size() >= 3) {
                                    VBox overdueCard = (VBox) summaryCards.getChildren().get(2);
                                    Label overdueLabel = (Label) overdueCard.getChildren().get(1);
                                    overdueLabel.setText(String.valueOf(getOverdueCount()));
                                }
                            }
                        }
                    });
                })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void performSearch(String query, String searchType) {
        if (query == null || query.trim().isEmpty()) {
            loadBooksFromDatabase();
            return;
        }

        showLoadingIndicator(true);
        booksFlowPane.getChildren().clear();

        Task<List<Book>> searchTask = new Task<>() {
            @Override
            protected List<Book> call() throws Exception {
                List<Book> results = new ArrayList<>();
                String sql = "SELECT * FROM books WHERE " + getSearchCondition(searchType) + " LIKE ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, "%" + query + "%");
                    ResultSet rs = pstmt.executeQuery();

                    while (rs.next()) {
                        results.add(new Book(
                                rs.getString("title"),
                                rs.getString("author"),
                                rs.getString("isbn"),
                                rs.getString("genre"),
                                rs.getString("shelf_number"),
                                rs.getString("status"),
                                rs.getInt("quantity"),
                                rs.getBytes("cover_image")
                        ));
                    }
                }
                return results;
            }
        };

        searchTask.setOnSucceeded(e -> {
            List<Book> results = searchTask.getValue();
            Platform.runLater(() -> {
                booksFlowPane.getChildren().clear();
                for (Book book : results) {
                    addBookCardWithAnimation(book);
                }
                showLoadingIndicator(false);
            });
        });

        searchTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showLoadingIndicator(false);
                showAlert("Error", "Search failed: " + searchTask.getException().getMessage());
            });
        });

        new Thread(searchTask).start();
    }

    private String getSearchCondition(String searchType) {
        switch (searchType) {
            case "Title":
                return "title";
            case "Author":
                return "author";
            case "ISBN":
                return "isbn";
            case "Genre":
                return "genre";
            case "Shelf":
                return "shelf_number";
            case "Status":
                return "status";
            default:
                return "title";
        }
    }

    private void showAddBookDialog() {
        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle("Add New Book");
        dialog.setHeaderText("Enter book details");

        final byte[][] coverImageHolder = new byte[1][];

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        TextField titleField = new TextField();
        titleField.setPromptText("Title");
        styleTextField(titleField);

        TextField authorField = new TextField();
        authorField.setPromptText("Author");
        styleTextField(authorField);

        TextField isbnField = new TextField();
        isbnField.setPromptText("ISBN");
        styleTextField(isbnField);

        TextField genreField = new TextField();
        genreField.setPromptText("Genre");
        styleTextField(genreField);

        TextField shelfField = new TextField();
        shelfField.setPromptText("Shelf Number");
        styleTextField(shelfField);

        Spinner<Integer> quantityField = new Spinner<>(1, 100, 1);
        styleSpinner(quantityField);

        Button uploadButton = new Button("Upload Cover");
        styleButton(uploadButton, "#6c757d", "#5a6268", "#4e555b");
        ImageView coverPreview = new ImageView();
        coverPreview.setFitWidth(150);
        coverPreview.setFitHeight(200);
        coverPreview.setPreserveRatio(true);

        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Book Cover");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                try {
                    coverImageHolder[0] = Files.readAllBytes(selectedFile.toPath());
                    coverPreview.setImage(new Image(new FileInputStream(selectedFile)));
                } catch (IOException ex) {
                    showAlert("Error", "Could not load image: " + ex.getMessage());
                }
            }
        });

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Author:"), 0, 1);
        grid.add(authorField, 1, 1);
        grid.add(new Label("ISBN:"), 0, 2);
        grid.add(isbnField, 1, 2);
        grid.add(new Label("Genre:"), 0, 3);
        grid.add(genreField, 1, 3);
        grid.add(new Label("Shelf:"), 0, 4);
        grid.add(shelfField, 1, 4);
        grid.add(new Label("Quantity:"), 0, 5);
        grid.add(quantityField, 1, 5);
        grid.add(new Label("Cover:"), 0, 6);
        grid.add(uploadButton, 1, 6);
        grid.add(coverPreview, 1, 7);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new Book(
                        titleField.getText(),
                        authorField.getText(),
                        isbnField.getText(),
                        genreField.getText(),
                        shelfField.getText(),
                        "Available",
                        quantityField.getValue(),
                        coverImageHolder[0]
                );
            }
            return null;
        });

        Optional<Book> result = dialog.showAndWait();

        result.ifPresent(book -> {
            try {
                addBookToDatabase(book);
                showBooksContent();
                showAlert("Success", "Book added successfully!");
            } catch (SQLException ex) {
                showAlert("Error", "Could not add book: " + ex.getMessage());
            }
        });
    }

    private void styleSpinner(Spinner<Integer> spinner) {
        spinner.setStyle("-fx-background-color: white; -fx-border-color: #ccc; "
                + "-fx-border-radius: 5; -fx-padding: 5; -fx-font-size: 14;");
    }

    private void addBookToDatabase(Book book) throws SQLException {
        String sql = "INSERT INTO books (title, author, isbn, genre, shelf_number, status, quantity, cover_image) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getTitle());
            pstmt.setString(2, book.getAuthor());
            pstmt.setString(3, book.getIsbn());
            pstmt.setString(4, book.getGenre());
            pstmt.setString(5, book.getShelfNumber());
            pstmt.setString(6, book.getStatus());
            pstmt.setInt(7, book.getQuantity());
            pstmt.setBytes(8, book.getCoverImage());
            pstmt.executeUpdate();
        }
    }

    private void showEditBookDialog(Book book) {
        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle("Edit Book");
        dialog.setHeaderText("Edit book details");

        // Create a copy of the book to edit
        Book editedBook = new Book(
                book.getTitle(), book.getAuthor(), book.getIsbn(), book.getGenre(),
                book.getShelfNumber(), book.getStatus(), book.getQuantity(), book.getCoverImage()
        );

        final byte[][] newCoverImageHolder = new byte[1][];
        newCoverImageHolder[0] = book.getCoverImage();

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        TextField titleField = new TextField(book.getTitle());
        styleTextField(titleField);

        TextField authorField = new TextField(book.getAuthor());
        styleTextField(authorField);

        TextField isbnField = new TextField(book.getIsbn());
        isbnField.setDisable(true);
        styleTextField(isbnField);

        TextField genreField = new TextField(book.getGenre());
        styleTextField(genreField);

        TextField shelfField = new TextField(book.getShelfNumber());
        styleTextField(shelfField);

        Spinner<Integer> quantityField = new Spinner<>(1, 100, book.getQuantity());
        styleSpinner(quantityField);

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Available", "On Loan", "Reserved", "Damaged", "Lost");
        statusCombo.setValue(book.getStatus());
        styleComboBox(statusCombo);

        Button uploadButton = new Button("Change Cover");
        styleButton(uploadButton, "#6c757d", "#5a6268", "#4e555b");
        ImageView coverPreview = new ImageView();
        coverPreview.setFitWidth(150);
        coverPreview.setFitHeight(200);
        coverPreview.setPreserveRatio(true);

        if (book.getCoverImage() != null) {
            coverPreview.setImage(new Image(new ByteArrayInputStream(book.getCoverImage())));
        }

        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Book Cover");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                try {
                    newCoverImageHolder[0] = Files.readAllBytes(selectedFile.toPath());
                    coverPreview.setImage(new Image(new FileInputStream(selectedFile)));
                } catch (IOException ex) {
                    showAlert("Error", "Could not load image: " + ex.getMessage());
                }
            }
        });

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Author:"), 0, 1);
        grid.add(authorField, 1, 1);
        grid.add(new Label("ISBN:"), 0, 2);
        grid.add(isbnField, 1, 2);
        grid.add(new Label("Genre:"), 0, 3);
        grid.add(genreField, 1, 3);
        grid.add(new Label("Shelf:"), 0, 4);
        grid.add(shelfField, 1, 4);
        grid.add(new Label("Quantity:"), 0, 5);
        grid.add(quantityField, 1, 5);
        grid.add(new Label("Status:"), 0, 6);
        grid.add(statusCombo, 1, 6);
        grid.add(new Label("Cover:"), 0, 7);
        grid.add(uploadButton, 1, 7);
        grid.add(coverPreview, 1, 8);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                // Update the original book with edited values
                book.setTitle(titleField.getText());
                book.setAuthor(authorField.getText());
                book.setGenre(genreField.getText());
                book.setShelfNumber(shelfField.getText());
                book.setStatus(statusCombo.getValue());
                book.setQuantity(quantityField.getValue());
                book.setCoverImage(newCoverImageHolder[0]);

                try {
                    updateBookInDatabase(book);
                    return book;
                } catch (SQLException ex) {
                    showAlert("Error", "Could not update book: " + ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<Book> result = dialog.showAndWait();
        result.ifPresent(updatedBook -> {
            showAlert("Success", "Book updated successfully!");
        });
    }

    private void updateBookInDatabase(Book book) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ?, genre = ?, shelf_number = ?, "
                + "status = ?, quantity = ?, cover_image = ? WHERE isbn = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getTitle());
            pstmt.setString(2, book.getAuthor());
            pstmt.setString(3, book.getGenre());
            pstmt.setString(4, book.getShelfNumber());
            pstmt.setString(5, book.getStatus());
            pstmt.setInt(6, book.getQuantity());
            pstmt.setBytes(7, book.getCoverImage());
            pstmt.setString(8, book.getIsbn());
            pstmt.executeUpdate();
        }
    }

    private void deleteBook(Book book) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Book");
        alert.setContentText("Are you sure you want to delete '" + book.getTitle() + "'?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                deleteBookFromDatabase(book.getIsbn());
                showBooksContent();
                showAlert("Success", "Book deleted successfully!");
            } catch (SQLException ex) {
                showAlert("Error", "Could not delete book: " + ex.getMessage());
            }
        }
    }

    private void deleteBookFromDatabase(String isbn) throws SQLException {
        String sql = "DELETE FROM books WHERE isbn = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            pstmt.executeUpdate();
        }
    }

    private void showLoanBookDialog(Book book) {
        int available = getAvailableCount(book.getIsbn());
        if (available <= 0) {
            showAlert("Not Available", "This book is currently not available for loan.");
            return;
        }

        Dialog<Loan> dialog = new Dialog<>();
        dialog.setTitle("Loan Book");
        dialog.setHeaderText("Loan '" + book.getTitle() + "' to a borrower");

        ButtonType loanButtonType = new ButtonType("Loan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loanButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        TextField borrowerIdField = new TextField();
        borrowerIdField.setPromptText("Borrower ID*");
        styleTextField(borrowerIdField);

        TextField borrowerNameField = new TextField();
        borrowerNameField.setPromptText("Borrower Name*");
        styleTextField(borrowerNameField);

        DatePicker loanDatePicker = new DatePicker(LocalDate.now());
        Spinner<Integer> loanHourPicker = new Spinner<>(0, 23, LocalTime.now().getHour());
        Spinner<Integer> loanMinutePicker = new Spinner<>(0, 59, LocalTime.now().getMinute());
        HBox loanTimeBox = new HBox(5, loanHourPicker, new Label(":"), loanMinutePicker);

        DatePicker returnDatePicker = new DatePicker(LocalDate.now().plusWeeks(2));
        Spinner<Integer> returnHourPicker = new Spinner<>(0, 23, 17);
        Spinner<Integer> returnMinutePicker = new Spinner<>(0, 59, 0);
        HBox returnTimeBox = new HBox(5, returnHourPicker, new Label(":"), returnMinutePicker);

        Label availableLabel = new Label("Available copies: " + available);
        availableLabel.setStyle("-fx-font-weight: bold;");

        Label requiredLabel = new Label("* Required fields");
        requiredLabel.setStyle("-fx-font-style: italic;");

        grid.add(new Label("Borrower ID*:"), 0, 0);
        grid.add(borrowerIdField, 1, 0);
        grid.add(new Label("Borrower Name*:"), 0, 1);
        grid.add(borrowerNameField, 1, 1);
        grid.add(new Label("Loan Date:"), 0, 2);
        grid.add(loanDatePicker, 1, 2);
        grid.add(new Label("Loan Time:"), 0, 3);
        grid.add(loanTimeBox, 1, 3);
        grid.add(new Label("Return Date:"), 0, 4);
        grid.add(returnDatePicker, 1, 4);
        grid.add(new Label("Return Time:"), 0, 5);
        grid.add(returnTimeBox, 1, 5);
        grid.add(availableLabel, 0, 6, 2, 1);
        grid.add(requiredLabel, 0, 7, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Node loanButton = dialog.getDialogPane().lookupButton(loanButtonType);
        loanButton.setDisable(true);

        ChangeListener<String> inputListener = (observable, oldValue, newValue) -> {
            boolean fieldsFilled = !borrowerIdField.getText().trim().isEmpty()
                    && !borrowerNameField.getText().trim().isEmpty();
            loanButton.setDisable(!fieldsFilled);
        };

        borrowerIdField.textProperty().addListener(inputListener);
        borrowerNameField.textProperty().addListener(inputListener);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loanButtonType) {
                if (borrowerIdField.getText().trim().isEmpty() || borrowerNameField.getText().trim().isEmpty()) {
                    showAlert("Validation Error", "Borrower ID and Name are required fields");
                    return null;
                }

                LocalDateTime loanDateTime = LocalDateTime.of(
                        loanDatePicker.getValue(),
                        LocalTime.of(loanHourPicker.getValue(), loanMinutePicker.getValue())
                );
                LocalDateTime returnDateTime = LocalDateTime.of(
                        returnDatePicker.getValue(),
                        LocalTime.of(returnHourPicker.getValue(), returnMinutePicker.getValue())
                );
                return new Loan(
                        book.getIsbn(),
                        borrowerIdField.getText().trim(),
                        borrowerNameField.getText().trim(),
                        loanDateTime,
                        returnDateTime
                );
            }
            return null;
        });

        Optional<Loan> result = dialog.showAndWait();

        result.ifPresent(loan -> {
            try {
                addLoanToDatabase(loan);
                updateBookStatus(book.getIsbn(), "On Loan");
                showBooksContent();
                showAlert("Success", "Book loan recorded successfully!");
            } catch (SQLException ex) {
                showAlert("Error", "Could not record loan: " + ex.getMessage());
            }
        });
    }

    private void addLoanToDatabase(Loan loan) throws SQLException {
        String sql = "INSERT INTO loans (isbn, borrower_id, borrower_name, loan_date, return_date) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, loan.getIsbn());
            pstmt.setString(2, loan.getBorrowerId());
            pstmt.setString(3, loan.getBorrowerName());
            pstmt.setTimestamp(4, Timestamp.valueOf(loan.getLoanDate()));
            pstmt.setTimestamp(5, Timestamp.valueOf(loan.getReturnDate()));
            pstmt.executeUpdate();
        }
    }

    private void updateBookStatus(String isbn, String status) throws SQLException {
        String sql = "UPDATE books SET status = ? WHERE isbn = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, isbn);
            pstmt.executeUpdate();
        }
    }

    private void showLoansContent() {
        titleLabel.setText("Loan Management - Yemedemer Tiwlid Library");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        TableView<Loan> loanTable = new TableView<>();
        loanTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Loan, String> bookCol = new TableColumn<>("Book");
        bookCol.setCellValueFactory(cellData -> {
            String isbn = cellData.getValue().getIsbn();
            try {
                return getBookTitleProperty(isbn);
            } catch (SQLException e) {
                return new SimpleStringProperty("Unknown");
            }
        });

        TableColumn<Loan, String> borrowerCol = new TableColumn<>("Borrower");
        borrowerCol.setCellValueFactory(cellData -> cellData.getValue().borrowerNameProperty());

        TableColumn<Loan, String> loanDateCol = new TableColumn<>("Loan Date");
        loanDateCol.setCellValueFactory(cellData -> cellData.getValue().loanDateProperty());

        TableColumn<Loan, String> returnDateCol = new TableColumn<>("Return Date");
        returnDateCol.setCellValueFactory(cellData -> cellData.getValue().returnDateProperty());

        TableColumn<Loan, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> {
            Loan loan = cellData.getValue();
            if (loan.isReturned()) {
                return new SimpleStringProperty("Returned");
            } else if (loan.getReturnDate().isBefore(LocalDateTime.now())) {
                return new SimpleStringProperty("Overdue");
            } else {
                return new SimpleStringProperty("On Loan");
            }
        });

        TableColumn<Loan, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button returnButton = new Button("Return");

            {
                styleButton(returnButton, "#28a745", "#34ce57", "#218838");
                returnButton.setOnAction(event -> {
                    Loan loan = getTableView().getItems().get(getIndex());
                    returnBook(loan);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()).isReturned()) {
                    setGraphic(null);
                } else {
                    setGraphic(returnButton);
                }
            }
        });

        loanTable.getColumns().addAll(bookCol, borrowerCol, loanDateCol, returnDateCol, statusCol, actionsCol);

        String sql = "SELECT * FROM loans ORDER BY returned, return_date";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            loanTable.getItems().clear();

            while (rs.next()) {
                Loan loan = new Loan(
                        rs.getString("isbn"),
                        rs.getString("borrower_id"),
                        rs.getString("borrower_name"),
                        rs.getTimestamp("loan_date").toLocalDateTime(),
                        rs.getTimestamp("return_date").toLocalDateTime(),
                        rs.getBoolean("returned")
                );
                loan.setId(rs.getInt("id"));
                loanTable.getItems().add(loan);
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not load loans: " + e.getMessage());
        }

        content.getChildren().add(loanTable);
        mainLayout.setCenter(content);
    }

    private StringProperty getBookTitleProperty(String isbn) throws SQLException {
        String sql = "SELECT title FROM books WHERE isbn = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? new SimpleStringProperty(rs.getString("title")) : new SimpleStringProperty("Unknown");
        }
    }

    private void returnBook(Loan loan) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Return");
        alert.setHeaderText("Return Book");
        alert.setContentText("Are you sure you want to mark this book as returned?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                markLoanAsReturned(loan.getId());
                updateBookStatusIfAvailable(loan.getIsbn());
                showLoansContent();

                if (mainLayout.getCenter() instanceof VBox) {
                    VBox center = (VBox) mainLayout.getCenter();
                    if (center.getChildren().size() > 0
                            && center.getChildren().get(0) instanceof Button
                            && ((Button) center.getChildren().get(0)).getText().equals("Refresh Charts")) {
                        showAnalysisContent();
                    }
                }

                if (booksFlowPane != null) {
                    loadBooksFromDatabase();
                }
                showAlert("Success", "Book returned successfully!");
            } catch (SQLException ex) {
                showAlert("Error", "Could not return book: " + ex.getMessage());
            }
        }
    }

    private void markLoanAsReturned(int loanId) throws SQLException {
        String sql = "UPDATE loans SET returned = TRUE WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, loanId);
            pstmt.executeUpdate();
        }
    }

    private void updateBookStatusIfAvailable(String isbn) throws SQLException {
        validateConnection();

        String sql = "SELECT COUNT(*) FROM loans WHERE isbn = ? AND returned = 0";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                sql = "UPDATE books SET status = 'Available' WHERE isbn = ?";
                try (PreparedStatement pstmt2 = connection.prepareStatement(sql)) {
                    pstmt2.setString(1, isbn);
                    pstmt2.executeUpdate();
                }
            }
        }
    }

    private void showSearchContent() {
        titleLabel.setText("Search Books - Yemedemer Tiwlid Library");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        HBox searchControls = new HBox(10);
        searchControls.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search books...");
        searchField.setPrefWidth(400);
        styleTextField(searchField);

        searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll("Title", "Author", "ISBN", "Genre", "Shelf", "Status");
        searchTypeCombo.setValue("Title");
        styleComboBox(searchTypeCombo);

        Button searchButton = new Button("Search");
        styleButton(searchButton, "#2a9df4", "#3aa8ff", "#1a7bc8");
        searchButton.setOnAction(e -> performSearch(searchField.getText(), searchTypeCombo.getValue()));

        searchControls.getChildren().addAll(searchField, searchTypeCombo, searchButton);

        booksFlowPane = new FlowPane();
        booksFlowPane.setPadding(new Insets(15));
        booksFlowPane.setHgap(20);
        booksFlowPane.setVgap(20);
        booksFlowPane.setStyle("-fx-background-color: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

        ScrollPane scrollPane = new ScrollPane(booksFlowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

        content.getChildren().addAll(searchControls, scrollPane);
        mainLayout.setCenter(content);
    }

    private void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                ProgressIndicator progress = new ProgressIndicator();
                progress.setMaxSize(50, 50);
                progress.setId("loading-indicator");
                StackPane.setAlignment(progress, Pos.CENTER);
                root.getChildren().add(progress);
            } else {
                root.getChildren().removeIf(node
                        -> node instanceof ProgressIndicator
                        || "loading-indicator".equals(node.getId()));
            }
        });
    }

    private void showAnalysisContent() {
        titleLabel.setText("Library Analysis - Yemedemer Tiwlid Library");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Button exportButton = new Button("Export to Excel");
        styleButton(exportButton, "#28a745", "#34ce57", "#218838");
        exportButton.setOnAction(e -> exportAnalysisData());

        Button refreshButton = new Button("Refresh Charts");
        styleButton(refreshButton, "#2a9df4", "#3aa8ff", "#1a7bc8");
        refreshButton.setOnAction(e -> showAnalysisContent());

        VBox chartsBox = new VBox(20);

        PieChart genreChart = createGenrePieChart();
        genreChart.setTitle("Books by Genre");
        genreChart.setLegendVisible(true);

        BarChart<String, Number> loanStatusChart = createLoanStatusChart();
        loanStatusChart.setTitle("Loan Status");

        LineChart<String, Number> monthlyLoansChart = createMonthlyLoansChart();
        monthlyLoansChart.setTitle("Monthly Loans");

        HBox chartRow1 = new HBox(20, genreChart, loanStatusChart);
        chartRow1.setAlignment(Pos.CENTER);

        HBox controlsBox = new HBox(20, exportButton, refreshButton);
        controlsBox.setAlignment(Pos.CENTER_LEFT);

        chartsBox.getChildren().addAll(controlsBox, chartRow1, monthlyLoansChart);

        ScrollPane scrollPane = new ScrollPane(chartsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + (darkMode ? DARK_BG : LIGHT_BG) + ";");

        content.getChildren().add(scrollPane);
        mainLayout.setCenter(content);
    }

    private PieChart createGenrePieChart() {
        PieChart pieChart = new PieChart();
        pieChart.setTitle("Books by Genre");
        pieChart.setLegendVisible(true);
        pieChart.setLabelsVisible(true);

        String sql = "SELECT genre, SUM(quantity) AS total FROM books GROUP BY genre";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            while (rs.next()) {
                String genre = rs.getString("genre");
                int total = rs.getInt("total");

                String label = genre + " (" + total + ")";
                PieChart.Data slice = new PieChart.Data(label, total);
                pieChartData.add(slice);
            }

            pieChart.setData(pieChartData);

            for (PieChart.Data data : pieChart.getData()) {
                Tooltip tooltip = new Tooltip(
                        data.getName() + ": " + (int) data.getPieValue() + " books"
                );
                Tooltip.install(data.getNode(), tooltip);
            }

        } catch (SQLException e) {
            showAlert("Error", "Could not load genre data: " + e.getMessage());
        }

        return pieChart;
    }

    private BarChart<String, Number> createLoanStatusChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Loan Status");

        try {
            String sqlActive = "SELECT COUNT(*) FROM loans WHERE returned = 0 AND return_date >= ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlActive)) {
                pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    series.getData().add(new XYChart.Data<>("Active", rs.getInt(1)));
                }
            }

            String sqlOverdue = "SELECT COUNT(*) FROM loans WHERE returned = 0 AND return_date < ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlOverdue)) {
                pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    series.getData().add(new XYChart.Data<>("Overdue", rs.getInt(1)));
                }
            }

            String sqlReturned = "SELECT COUNT(*) FROM loans WHERE returned = 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlReturned)) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    series.getData().add(new XYChart.Data<>("Returned", rs.getInt(1)));
                }
            }

            barChart.getData().add(series);
        } catch (SQLException e) {
            showAlert("Error", "Could not load loan status data: " + e.getMessage());
        }

        return barChart;
    }

    private LineChart<String, Number> createMonthlyLoansChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Loans per Month");

        String sql = "SELECT DATE_FORMAT(loan_date, '%Y-%m') as month, COUNT(*) as count "
                + "FROM loans WHERE loan_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) "
                + "GROUP BY month ORDER BY month";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(
                        rs.getString("month"),
                        rs.getInt("count")
                ));
            }

            lineChart.getData().add(series);
        } catch (SQLException e) {
            showAlert("Error", "Could not load monthly loans data: " + e.getMessage());
        }

        return lineChart;
    }

    private void exportAnalysisData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Analysis Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("yeme_demer_tiwlid_library_analysis.csv");

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {

                writer.println("Book Summary");
                writer.println("Genre,Book Count");

                String genreSql = "SELECT genre, SUM(quantity) as total FROM books GROUP BY genre";
                try (PreparedStatement pstmt = connection.prepareStatement(genreSql)) {
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        writer.printf("%s,%d%n", rs.getString("genre"), rs.getInt("total"));
                    }
                }

                writer.println();

                writer.println("Loan Status");
                writer.println("Status,Count");

                String activeSql = "SELECT COUNT(*) FROM loans WHERE returned = 0";
                String overdueSql = "SELECT COUNT(*) FROM loans WHERE returned = 0 AND return_date < ?";
                String returnedSql = "SELECT COUNT(*) FROM loans WHERE returned = 1";

                try (PreparedStatement pstmt = connection.prepareStatement(activeSql)) {
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        writer.printf("Active Loans,%d%n", rs.getInt(1));
                    }
                }

                try (PreparedStatement pstmt = connection.prepareStatement(overdueSql)) {
                    pstmt.setString(1, LocalDate.now().toString());
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        writer.printf("Overdue Loans,%d%n", rs.getInt(1));
                    }
                }

                try (PreparedStatement pstmt = connection.prepareStatement(returnedSql)) {
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        writer.printf("Returned Loans,%d%n", rs.getInt(1));
                    }
                }

                writer.println();

                writer.println("Monthly Loans");
                writer.println("Month,Loan Count");

                String monthlySql = "SELECT DATE_FORMAT(loan_date, '%Y-%m') as month, COUNT(*) as count "
                        + "FROM loans WHERE loan_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) "
                        + "GROUP BY month ORDER BY month";

                try (PreparedStatement pstmt = connection.prepareStatement(monthlySql)) {
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        writer.printf("%s,%d%n", rs.getString("month"), rs.getInt("count"));
                    }
                }

                showAlert("Success", "Data exported successfully to " + file.getName());

            } catch (Exception e) {
                showAlert("Error", "Could not export data: " + e.getMessage());
            }
        }
    }

    private void showSettingsContent() {
        titleLabel.setText("Settings");

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        Label settingsLabel = new Label("Application Settings");
        settingsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        settingsLabel.setTextFill(darkMode ? Color.web(DARK_TEXT) : Color.BLACK);

        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.setSelected(darkMode);
        themeToggle.setOnAction(e -> {
            darkMode = themeToggle.isSelected();
            showMainDashboard();
        });

        content.getChildren().addAll(settingsLabel, themeToggle);
        mainLayout.setCenter(content);
    }

    private void showDevelopersWindow() {
        Stage developersStage = new Stage();
        developersStage.setTitle("Development Team");

        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Development Team");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        class Developer {

            String name;
            String id;

            Developer(String name, String id) {
                this.name = name;
                this.id = id;
            }
        }

        List<Developer> developers = Arrays.asList(
                new Developer("Abel Ayele", "DDU1500785"),
                new Developer("Hebron Solomon", "DDU1502273"),
                new Developer("Nebiyu Ermiyas", "DDU1501530"),
                new Developer("Trhas Abrha", "DDU1502253"),
                new Developer("Yabsira Dejene", "DDU1501750"),
                new Developer("Yenus Kindu", "DDU1501779")
        );

        GridPane developersGrid = new GridPane();
        developersGrid.setHgap(20);
        developersGrid.setVgap(10);
        developersGrid.setPadding(new Insets(10));

        developersGrid.add(new Label("Name"), 0, 0);
        developersGrid.add(new Label("ID"), 1, 0);

        for (int i = 0; i < developers.size(); i++) {
            Developer dev = developers.get(i);
            developersGrid.add(new Label(dev.name), 0, i + 1);
            developersGrid.add(new Label(dev.id), 1, i + 1);
        }

        Button closeButton = new Button("Close");
        styleButton(closeButton, "#2a9df4", "#3aa8ff", "#1a7bc8");
        closeButton.setOnAction(e -> developersStage.close());

        root.getChildren().addAll(titleLabel, developersGrid, closeButton);

        if (darkMode) {
            root.setStyle("-fx-background-color: " + DARK_BG + ";");
            titleLabel.setTextFill(Color.web(DARK_TEXT));
            developersGrid.setStyle("-fx-background-color: " + DARK_CARD + ";");
            for (Node node : developersGrid.getChildren()) {
                if (node instanceof Label) {
                    ((Label) node).setTextFill(Color.web(DARK_TEXT));
                }
            }
        }

        Scene scene = new Scene(root, 400, 400);
        developersStage.setScene(scene);
        developersStage.initModality(Modality.APPLICATION_MODAL);
        developersStage.show();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Model classes
    public static class User {

        private String fullName;
        private String username;
        private String password;
        private boolean isAdmin;

        public User(String fullName, String username, String password, boolean isAdmin) {
            this.fullName = fullName;
            this.username = username;
            this.password = password;
            this.isAdmin = isAdmin;
        }

        public String getFullName() {
            return fullName;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public boolean isAdmin() {
            return isAdmin;
        }
    }

    public static class Book {

        private final StringProperty title = new SimpleStringProperty();
        private final StringProperty author = new SimpleStringProperty();
        private final StringProperty isbn = new SimpleStringProperty();
        private final StringProperty genre = new SimpleStringProperty();
        private final StringProperty shelfNumber = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final IntegerProperty quantity = new SimpleIntegerProperty();
        private final ObjectProperty<byte[]> coverImage = new SimpleObjectProperty<>();

        public Book(String title, String author, String isbn, String genre,
                String shelfNumber, String status, int quantity, byte[] coverImage) {
            setTitle(title);
            setAuthor(author);
            setIsbn(isbn);
            setGenre(genre);
            setShelfNumber(shelfNumber);
            setStatus(status);
            setQuantity(quantity);
            setCoverImage(coverImage);
        }

        // Getter methods for properties
        public StringProperty titleProperty() {
            return title;
        }

        public StringProperty authorProperty() {
            return author;
        }

        public StringProperty isbnProperty() {
            return isbn;
        }

        public StringProperty genreProperty() {
            return genre;
        }

        public StringProperty shelfNumberProperty() {
            return shelfNumber;
        }

        public StringProperty statusProperty() {
            return status;
        }

        public IntegerProperty quantityProperty() {
            return quantity;
        }

        public ObjectProperty<byte[]> coverImageProperty() {
            return coverImage;
        }

        // Traditional getters
        public String getTitle() {
            return title.get();
        }

        public String getAuthor() {
            return author.get();
        }

        public String getIsbn() {
            return isbn.get();
        }

        public String getGenre() {
            return genre.get();
        }

        public String getShelfNumber() {
            return shelfNumber.get();
        }

        public String getStatus() {
            return status.get();
        }

        public int getQuantity() {
            return quantity.get();
        }

        public byte[] getCoverImage() {
            return coverImage.get();
        }

        // Traditional setters
        public void setTitle(String title) {
            this.title.set(title);
        }

        public void setAuthor(String author) {
            this.author.set(author);
        }

        public void setIsbn(String isbn) {
            this.isbn.set(isbn);
        }

        public void setGenre(String genre) {
            this.genre.set(genre);
        }

        public void setShelfNumber(String shelfNumber) {
            this.shelfNumber.set(shelfNumber);
        }

        public void setStatus(String status) {
            this.status.set(status);
        }

        public void setQuantity(int quantity) {
            this.quantity.set(quantity);
        }

        public void setCoverImage(byte[] coverImage) {
            this.coverImage.set(coverImage);
        }
    }

    public static class Loan {

        private int id;
        private String isbn;
        private String borrowerId;
        private String borrowerName;
        private LocalDateTime loanDate;
        private LocalDateTime returnDate;
        private boolean returned;

        public Loan(String isbn, String borrowerId, String borrowerName,
                LocalDateTime loanDate, LocalDateTime returnDate) {
            this.isbn = isbn;
            this.borrowerId = borrowerId;
            this.borrowerName = borrowerName;
            this.loanDate = loanDate;
            this.returnDate = returnDate;
            this.returned = false;
        }

        public Loan(String isbn, String borrowerId, String borrowerName,
                LocalDateTime loanDate, LocalDateTime returnDate, boolean returned) {
            this(isbn, borrowerId, borrowerName, loanDate, returnDate);
            this.returned = returned;
        }

        public int getId() {
            return id;
        }

        public String getIsbn() {
            return isbn;
        }

        public String getBorrowerId() {
            return borrowerId;
        }

        public String getBorrowerName() {
            return borrowerName;
        }

        public LocalDateTime getLoanDate() {
            return loanDate;
        }

        public LocalDateTime getReturnDate() {
            return returnDate;
        }

        public boolean isReturned() {
            return returned;
        }

        public void setId(int id) {
            this.id = id;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public void setBorrowerId(String borrowerId) {
            this.borrowerId = borrowerId;
        }

        public void setBorrowerName(String borrowerName) {
            this.borrowerName = borrowerName;
        }

        public void setLoanDate(LocalDateTime loanDate) {
            this.loanDate = loanDate;
        }

        public void setReturnDate(LocalDateTime returnDate) {
            this.returnDate = returnDate;
        }

        public void setReturned(boolean returned) {
            this.returned = returned;
        }

        public StringProperty isbnProperty() {
            return new SimpleStringProperty(isbn);
        }

        public StringProperty borrowerIdProperty() {
            return new SimpleStringProperty(borrowerId);
        }

        public StringProperty borrowerNameProperty() {
            return new SimpleStringProperty(borrowerName);
        }

        public StringProperty loanDateProperty() {
            return new SimpleStringProperty(
                    loanDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
        }

        public StringProperty returnDateProperty() {
            return new SimpleStringProperty(
                    returnDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
        }

        public StringProperty statusProperty() {
            String status = returned
                    ? "Returned"
                    : (returnDate.isBefore(LocalDateTime.now()) ? "Overdue" : "On Loan");
            return new SimpleStringProperty(status);
        }
    }
}
