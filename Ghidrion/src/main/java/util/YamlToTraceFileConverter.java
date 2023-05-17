package util;

import java.awt.Component;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;

import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.util.Msg;
import model.Hook;
import model.Hook.Mode;
import model.MemoryEntry;
import model.MorionInitTraceFile;
import model.MorionTraceFile;
import view.HexDocument;

public class YamlToTraceFileConverter {
	
	/**
	 * Convert the information in the @param yamlStream to a {@link MorionInitTraceFile}.
	 * This method only converts information needed for a init trace file:
	 * <ul>
	 * 	<li>Hooks</li>
	 * 	<li>Entry state memory</li>
	 * 	<li>Entry state registers</li>
	 * </ul>
	 * 
	 * @param traceFile			{@link MorionInitTraceFile} to write to
	 * @param yamlStream		to write to @param traceFile
	 * @param addressFactory 	to create {@link Address} objects
	 * @param parent 
	 */
	public static void toInitTraceFile(MorionInitTraceFile traceFile, InputStream yamlStream, AddressFactory addressFactory, Component parent) {
		Map<String, Object> traceFileToConvert = loadTraceFile(yamlStream, parent);
		if (traceFileToConvert == null) {
			return;
		}
		
		addHooks(traceFile, traceFileToConvert, addressFactory, parent);
		addEntryMemory(traceFile, traceFileToConvert, parent);
		addEntryRegisters(traceFile, traceFileToConvert, parent);
	}

	/**
	 * Convert the information in the @param yamlStream to a {@link MorionTraceFile}.
	 * This method converts:
	 * <ul>
	 * 	<li>Hooks</li>
	 * 	<li>Entry state memory</li>
	 * 	<li>Entry state registers</li>
	 * 	<li>Leave state memory</li>
	 * 	<li>Leave state registers</li>
	 * </ul>
	 * 
	 * @param traceFile			{@link MorionTraceFile} to write to
	 * @param yamlStream		to write to @param traceFile
	 * @param addressFactory 	to create {@link Address} objects
	 * @param parent 
	 */
	public static void toTraceFile(MorionTraceFile traceFile, InputStream yamlStream, AddressFactory addressFactory, Component parent) {
		Map<String, Object> traceFileToConvert = loadTraceFile(yamlStream, parent);
		if (traceFileToConvert == null) {
			return;
		}
		
		addHooks(traceFile, traceFileToConvert, addressFactory, parent);
		addEntryMemory(traceFile, traceFileToConvert, parent);
		addEntryRegisters(traceFile, traceFileToConvert, parent);
		addLeaveMemory(traceFile, traceFileToConvert, parent);
		addLeaveRegisters(traceFile, traceFileToConvert, parent);
	}
	
	private static Map<String, Object> loadTraceFile(InputStream yamlStream, Component parent) {
		Map<String, Object> traceFileToConvert = null;
		try {
			traceFileToConvert = new Yaml().load(yamlStream);
		} catch (ParserException e) {
			Msg.showError(YamlToTraceFileConverter.class, parent, "Parser exception", e.getMessage(), e);
		}
		return traceFileToConvert;
	}
	
	private static void addHooks(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert, AddressFactory addressFactory, Component parent) {
		Map<String, Map<String, List<Map<String, String>>>> hookMap = (Map<String, Map<String, List<Map<String, String>>>>) traceFileToConvert
				.get(MorionInitTraceFile.HOOKS);
		Set<Hook> hooks = mapToHooks(hookMap, addressFactory, parent);
		if (hooks == null) {
			return;
		}
		traceFile.getHooks().replaceAll(hooks);
	}
	
	private static Set<Hook> mapToHooks(Map<String, Map<String, List<Map<String, String>>>> hookMap, AddressFactory addressFactory, Component parent) {
		Set<Hook> hooks = new HashSet<>();
		Map<String, List<Map<String, String>>> functions = hookMap.get("libc"); // Libc is hardcoded for now
		for (String functionName : functions.keySet()) {
			for (Map<String, String> hookDetails : functions.get(functionName)) {
				Address entry = getHookEntryAddress(functionName, hookDetails, addressFactory, parent);
				Mode mode = getHookMode(functionName, hookDetails, entry, parent);
				if (entry == null || mode == null) {
					return null;
				}

				try {
					hooks.add(new Hook(functionName, entry, mode));
				} catch (NullPointerException e) {
					String message = "Hook entry address " + entry + " is illegal"
							+ " (Function: " + functionName + ")";
					Msg.showError(YamlToTraceFileConverter.class, parent, "Illegal hook entry", message, e);
					return null;
				}
			}
		}
		return hooks;
	}
	
	private static Address getHookEntryAddress(String functionName, Map<String, String> hookDetails, AddressFactory addressFactory, Component parent) {
		if (! (hookDetails.containsKey(MorionInitTraceFile.HOOK_ENTRY))) {
			String message = "Hook entry address is missing (Function: " + functionName + ")";
			Msg.showError(YamlToTraceFileConverter.class, parent, "Entry missing", message);
			return null;
		}
		String entry = hookDetails.get(MorionInitTraceFile.HOOK_ENTRY);
		return addressFactory.getAddress(entry);
	}
	
	private static Mode getHookMode(String functionName, Map<String, String> hookDetails, Address entry, Component parent) {
		if (! (hookDetails.containsKey(MorionInitTraceFile.HOOK_MODE))) {
			String message = "Hook mode is missing (Function: " + functionName + ", Entry: " + entry + ")";
			Msg.showError(YamlToTraceFileConverter.class, parent, "Mode missing", message);
			return null;
		}
		Mode mode = null;
		try {
			mode = Mode.fromValue(hookDetails.get(MorionInitTraceFile.HOOK_MODE));
		} catch (IllegalArgumentException e) {
			String message = "Hook mode " + hookDetails.get(MorionInitTraceFile.HOOK_MODE) + " is illegal" 
					+ " (Function: " + functionName + ", Entry: " + entry + ")";
			Msg.showError(YamlToTraceFileConverter.class, parent, "Illegal hook mode", message, e);
		}
		return mode;
	}
	
	private static void addEntryMemory(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert, Component parent) {
		Map<String, Map<String, List<String>>> entryStateMap = getEntryStateMap(traceFileToConvert);
		if (entryStateMap != null && entryStateMap.containsKey(MorionInitTraceFile.STATE_MEMORY)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(entryStateMap.get(MorionInitTraceFile.STATE_MEMORY), parent);
			if (memoryEntries != null && hasValidMemoryStateAddresses(memoryEntries, parent)) {
				traceFile.getEntryMemory().replaceAll(memoryEntries);
			}
		}
	}
	
	private static void addEntryRegisters(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert, Component parent) {
		Map<String, Map<String, List<String>>> entryStateMap = getEntryStateMap(traceFileToConvert);
		if (entryStateMap != null && entryStateMap.containsKey(MorionInitTraceFile.STATE_REGISTERS)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(entryStateMap.get(MorionInitTraceFile.STATE_REGISTERS), parent);
			if (memoryEntries != null) {
				traceFile.getEntryRegisters().replaceAll(memoryEntries);
			}
		}
	}
	
	private static void addLeaveMemory(MorionTraceFile traceFile, Map<String, Object> traceFileToConvert, Component parent) {
		Map<String, Map<String, List<String>>> leaveStateMap = getLeaveStateMap(traceFileToConvert);
		if (leaveStateMap != null && leaveStateMap.containsKey(MorionInitTraceFile.STATE_MEMORY)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(leaveStateMap.get(MorionInitTraceFile.STATE_MEMORY), parent);
			if (memoryEntries != null && hasValidMemoryStateAddresses(memoryEntries, parent)) {
				traceFile.getLeaveMemory().replaceAll(memoryEntries);
			}
		}
	}
	
	private static void addLeaveRegisters(MorionTraceFile traceFile, Map<String, Object> traceFileToConvert, Component parent) {
		Map<String, Map<String, List<String>>> leaveStateMap = getLeaveStateMap(traceFileToConvert);
		if (leaveStateMap != null && leaveStateMap.containsKey(MorionInitTraceFile.STATE_REGISTERS)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(leaveStateMap.get(MorionInitTraceFile.STATE_REGISTERS), parent);
			if (memoryEntries != null) {
				traceFile.getLeaveRegisters().replaceAll(memoryEntries);
			}
		}
	}
	
	private static boolean hasValidMemoryStateAddresses(List<MemoryEntry> memoryEntries, Component parent) {
		boolean hasValidMemoryStateAddresses = true;
		for (MemoryEntry entry : memoryEntries) {
			if (! HexDocument.isValidHex(entry.getName())) {
				String message = "Memory state address '" + entry.getName() + "' has to be hexadecimal";
				Msg.showError(YamlToTraceFileConverter.class, parent, "Illegal memory state address", message);
				hasValidMemoryStateAddresses = false;
			}
		}
		return hasValidMemoryStateAddresses;
	}

	private static List<MemoryEntry> mapToMemoryEntries(Map<String, List<String>> entryMap, Component parent) {
		List<MemoryEntry> entries = new ArrayList<>();
		for (String name : entryMap.keySet()) {
			List<String> details = entryMap.get(name);
			if (details == null || details.size() <= 0) {
				String message = "State " + name + " has no value";
				Msg.showError(YamlToTraceFileConverter.class, parent, "Missing state value", message);
				return null;
			}
			String value = details.get(0);
			if (! HexDocument.isValidHex(value)) {
				String message = "State " + name + "'s value has to be hexadecimal";
				Msg.showError(YamlToTraceFileConverter.class, parent, "Illegal state value", message);
				return null;
			}
			boolean symbolic = details.size() > 1 
					&& MorionInitTraceFile.SYMBOLIC.equals(details.get(1));
			entries.add(new MemoryEntry(name, value, symbolic));
		}
		return entries;
	}
	
	private static Map<String, Map<String, List<String>>> getEntryStateMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, List<String>>> entryStateMap = null;
		Map<String, Map<String, Map<String, List<String>>>> statesMap = getStatesMap(traceFileToConvert);
		if (statesMap != null && statesMap.containsKey(MorionInitTraceFile.ENTRY_STATE)) {
			entryStateMap = statesMap.get(MorionInitTraceFile.ENTRY_STATE);
		}
		return entryStateMap;
	}
	
	private static Map<String, Map<String, List<String>>> getLeaveStateMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, List<String>>> leaveStateMap = null;
		Map<String, Map<String, Map<String, List<String>>>> statesMap = getStatesMap(traceFileToConvert);
		if (statesMap != null && statesMap.containsKey(MorionInitTraceFile.LEAVE_STATE)) {
			leaveStateMap = statesMap.get(MorionInitTraceFile.LEAVE_STATE);
		}
		return leaveStateMap;
	}
	
	private static Map<String, Map<String, Map<String, List<String>>>> getStatesMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, Map<String, List<String>>>> statesMap = null;
		if (traceFileToConvert.containsKey(MorionInitTraceFile.STATES)) {
			statesMap = (Map<String, Map<String, Map<String, List<String>>>>) traceFileToConvert.get(MorionInitTraceFile.STATES);
		}
		return statesMap;
	}

}
