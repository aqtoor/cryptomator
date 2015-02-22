/*******************************************************************************
 * Copyright (c) 2014 Sebastian Stenzel
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 * 
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 ******************************************************************************/
package org.cryptomator.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.cryptomator.ui.InitializeController.InitializationListener;
import org.cryptomator.ui.MainModule.ControllerFactory;
import org.cryptomator.ui.UnlockController.UnlockListener;
import org.cryptomator.ui.UnlockedController.LockListener;
import org.cryptomator.ui.controls.DirectoryListCell;
import org.cryptomator.ui.model.Vault;
import org.cryptomator.ui.model.VaultFactory;
import org.cryptomator.ui.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class MainController implements Initializable, InitializationListener, UnlockListener, LockListener {

	private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

	private Stage stage;

	@FXML
	private ContextMenu vaultListCellContextMenu;

	@FXML
	private ContextMenu addVaultContextMenu;

	@FXML
	private HBox rootPane;

	@FXML
	private ListView<Vault> vaultList;

	@FXML
	private ToggleButton addVaultButton;

	@FXML
	private Pane contentPane;

	private final ControllerFactory controllerFactory;
	private final Settings settings;
	private final VaultFactory vaultFactoy;

	private ResourceBundle rb;

	@Inject
	public MainController(ControllerFactory controllerFactory, Settings settings, VaultFactory vaultFactoy) {
		super();
		this.controllerFactory = controllerFactory;
		this.settings = settings;
		this.vaultFactoy = vaultFactoy;
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {
		this.rb = rb;

		final ObservableList<Vault> items = FXCollections.observableList(settings.getDirectories());
		vaultList.setItems(items);
		vaultList.setCellFactory(this::createDirecoryListCell);
		vaultList.getSelectionModel().getSelectedItems().addListener(this::selectedVaultDidChange);
	}

	@FXML
	private void didClickAddVault(ActionEvent event) {
		if (addVaultContextMenu.isShowing()) {
			addVaultContextMenu.hide();
		} else {
			addVaultContextMenu.show(addVaultButton, Side.RIGHT, 0.0, 0.0);
		}
	}

	@FXML
	private void willShowAddVaultContextMenu(WindowEvent event) {
		addVaultButton.setSelected(true);
	}

	@FXML
	private void didHideAddVaultContextMenu(WindowEvent event) {
		addVaultButton.setSelected(false);
	}

	@FXML
	private void didClickCreateNewVault(ActionEvent event) {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cryptomator vault", "*.cryptomator"));
		final File file = fileChooser.showSaveDialog(stage);
		try {
			if (file != null) {
				final Path vaultDir = Files.createDirectory(file.toPath());
				final Path vaultShortcutFile = vaultDir.resolve(vaultDir.getFileName());
				Files.createFile(vaultShortcutFile);
				addVault(vaultDir, true);
			}
		} catch (IOException e) {
			LOG.error("Unable to create vault", e);
		}
	}

	@FXML
	private void didClickAddExistingVaults(ActionEvent event) {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cryptomator vault", "*.cryptomator"));
		final List<File> files = fileChooser.showOpenMultipleDialog(stage);
		if (files != null) {
			for (final File file : files) {
				addVault(file.toPath(), false);
			}
		}
	}

	/**
	 * adds the given directory or selects it if it is already in the list of directories.
	 * 
	 * @param path non-null, writable, existing directory
	 */
	void addVault(final Path path, boolean select) {
		if (path == null || !Files.isWritable(path)) {
			return;
		}

		final Path vaultPath;
		if (path != null && Files.isDirectory(path)) {
			vaultPath = path;
		} else if (path != null && Files.isRegularFile(path) && path.getParent().getFileName().toString().endsWith(Vault.VAULT_FILE_EXTENSION)) {
			vaultPath = path.getParent();
		} else {
			return;
		}

		final Vault vault = vaultFactoy.createVault(vaultPath);
		if (!vaultList.getItems().contains(vault)) {
			vaultList.getItems().add(vault);
		}
		vaultList.getSelectionModel().select(vault);
	}

	private ListCell<Vault> createDirecoryListCell(ListView<Vault> param) {
		final DirectoryListCell cell = new DirectoryListCell();
		cell.setContextMenu(vaultListCellContextMenu);
		return cell;
	}

	private void selectedVaultDidChange(ListChangeListener.Change<? extends Vault> change) {
		final Vault selectedVault = vaultList.getSelectionModel().getSelectedItem();
		if (selectedVault == null) {
			stage.setTitle(rb.getString("app.name"));
			showWelcomeView();
		} else if (!Files.isDirectory(selectedVault.getPath())) {
			Platform.runLater(() -> {
				vaultList.getItems().remove(selectedVault);
				vaultList.getSelectionModel().clearSelection();
			});
			stage.setTitle(rb.getString("app.name"));
			showWelcomeView();
		} else {
			stage.setTitle(selectedVault.getName());
			showVault(selectedVault);
		}
	}

	@FXML
	private void didClickRemoveSelectedEntry(ActionEvent e) {
		final Vault selectedDir = vaultList.getSelectionModel().getSelectedItem();
		vaultList.getItems().remove(selectedDir);
		vaultList.getSelectionModel().clearSelection();
	}

	// ****************************************
	// Subcontroller for right panel
	// ****************************************

	private void showVault(Vault vault) {
		try {
			if (vault.isUnlocked()) {
				this.showUnlockedView(vault);
			} else if (vault.containsMasterKey()) {
				this.showUnlockView(vault);
			} else {
				this.showInitializeView(vault);
			}
		} catch (IOException e) {
			LOG.error("Failed to analyze directory.", e);
		}
	}

	private <T> T showView(String fxml) {
		try {
			final FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml), rb);
			loader.setControllerFactory(controllerFactory);
			final Parent root = loader.load();
			contentPane.getChildren().clear();
			contentPane.getChildren().add(root);
			return loader.getController();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load fxml file.", e);
		}
	}

	private void showWelcomeView() {
		this.showView("/fxml/welcome.fxml");
	}

	private void showInitializeView(Vault directory) {
		final InitializeController ctrl = showView("/fxml/initialize.fxml");
		ctrl.setDirectory(directory);
		ctrl.setListener(this);
	}

	@Override
	public void didInitialize(InitializeController ctrl) {
		showUnlockView(ctrl.getDirectory());
	}

	private void showUnlockView(Vault directory) {
		final UnlockController ctrl = showView("/fxml/unlock.fxml");
		ctrl.setVault(directory);
		ctrl.setListener(this);
	}

	@Override
	public void didUnlock(UnlockController ctrl) {
		showUnlockedView(ctrl.getVault());
		Platform.setImplicitExit(false);
	}

	private void showUnlockedView(Vault vault) {
		final UnlockedController ctrl = showView("/fxml/unlocked.fxml");
		ctrl.setVault(vault);
		ctrl.setListener(this);
	}

	@Override
	public void didLock(UnlockedController ctrl) {
		showUnlockView(ctrl.getVault());
		if (getUnlockedDirectories().isEmpty()) {
			Platform.setImplicitExit(true);
		}
	}

	/* Convenience */

	public Collection<Vault> getDirectories() {
		return vaultList.getItems();
	}

	public Collection<Vault> getUnlockedDirectories() {
		return getDirectories().stream().filter(d -> d.isUnlocked()).collect(Collectors.toSet());
	}

	/* public Getter/Setter */

	public Stage getStage() {
		return stage;
	}

	public void setStage(Stage stage) {
		this.stage = stage;
	}

	/**
	 * Attempts to make the application window visible.
	 */
	public void toFront() {
		stage.setIconified(false);
		stage.show();
		stage.toFront();
	}

}
