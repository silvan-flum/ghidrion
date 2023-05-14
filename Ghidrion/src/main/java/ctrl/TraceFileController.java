package ctrl;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;

import javax.swing.JFileChooser;
import javax.swing.JTable;

import ghidra.util.Msg;
import ghidrion.GhidrionPlugin;
import model.MemoryEntry;
import model.MorionTraceFile;
import util.MemoryEntryTableModel;
import util.TraceFileToYamlConverter;

public class TraceFileController {
	private final GhidrionPlugin plugin;
	private final MorionTraceFile traceFile;

	public TraceFileController(GhidrionPlugin plugin, MorionTraceFile traceFile) {
		this.plugin = Objects.requireNonNull(plugin);
		this.traceFile = Objects.requireNonNull(traceFile);
	}

	public GhidrionPlugin getPlugin() {
		return plugin;
	}

	public MorionTraceFile getTraceFile() {
		return traceFile;
	}

	/**
	 * Write the information in the @param tracefile to a `.yaml` file on disk.
	 * 
	 * @param parent to show the Save As dialog from
	 */
	public void writeTraceFile(Component parent) {
		String content = TraceFileToYamlConverter.toYaml(traceFile);

		JFileChooser fileChooser = new JFileChooser();
		int result = fileChooser.showSaveDialog(parent);
		File file = null;
		if (result == JFileChooser.APPROVE_OPTION) {
			file = fileChooser.getSelectedFile();
		}

		if (file != null) {
			try (FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(content.getBytes());
				fos.close();
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	public void clearTraceFileListener(ActionEvent e) {
		traceFile.clear();
	}

	public void addEntryMemory(
			String startAddress,
			String endAddress,
			String value,
			boolean isSymbolic,
			Component component) {
		long startAddressLong = Long.parseLong(startAddress.substring(2), 16);
		long endAddressLong = Long.parseLong(endAddress.substring(2), 16);
		if (startAddressLong > endAddressLong)
			Msg.showError(this, component, "Illegal End Address",
					"End Address has to be bigger or equal to Start Address.");
		else
			traceFile.getEntryMemory().replaceAll(LongStream
					.rangeClosed(startAddressLong, endAddressLong)
					.mapToObj(i -> new MemoryEntry("0x" + Long.toString(i, 16), value, isSymbolic))
					.toList());
	}

	public void removeAllEntryMemory(JTable tableMemory) {
		MemoryEntryTableModel model = (MemoryEntryTableModel) tableMemory.getModel();
		List<MemoryEntry> toDelete = model.getElementsAtRowIndices(tableMemory.getSelectedRows());
		traceFile.getEntryMemory().removeAll(toDelete);
	}

	public void addEntryRegister(String name, String value, boolean isSymbolic) {
		traceFile.getEntryRegisters().replace(new MemoryEntry(name, value, isSymbolic));
	}

	public void removeAllEntryRegisters(JTable tableRegister) {
		MemoryEntryTableModel model = (MemoryEntryTableModel) tableRegister.getModel();
		List<MemoryEntry> toDelete = model.getElementsAtRowIndices(tableRegister.getSelectedRows());
		traceFile.getEntryRegisters().removeAll(toDelete);
	}

}
