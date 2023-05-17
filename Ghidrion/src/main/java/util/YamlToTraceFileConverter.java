package util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;

import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
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
	 * @throws YamlConverterException 
	 */
	public static void toInitTraceFile(MorionInitTraceFile traceFile, InputStream yamlStream, AddressFactory addressFactory) throws YamlConverterException {
		Map<String, Object> traceFileToConvert = loadTraceFile(yamlStream);
		
		addHooks(traceFile, traceFileToConvert, addressFactory);
		addEntryMemory(traceFile, traceFileToConvert);
		addEntryRegisters(traceFile, traceFileToConvert);
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
	 * @throws YamlConverterException 
	 */
	public static void toTraceFile(MorionTraceFile traceFile, InputStream yamlStream, AddressFactory addressFactory) throws YamlConverterException {
		Map<String, Object> traceFileToConvert = loadTraceFile(yamlStream);
		
		addHooks(traceFile, traceFileToConvert, addressFactory);
		addEntryMemory(traceFile, traceFileToConvert);
		addEntryRegisters(traceFile, traceFileToConvert);
		addLeaveMemory(traceFile, traceFileToConvert);
		addLeaveRegisters(traceFile, traceFileToConvert);
	}
	
	private static Map<String, Object> loadTraceFile(InputStream yamlStream) throws YamlConverterException {
		Map<String, Object> traceFileToConvert;
		try {
			traceFileToConvert = new Yaml().load(yamlStream);
		} catch (ParserException e) {
			throw new YamlConverterException("Parser exception", e.getMessage(), e);
		}
		return traceFileToConvert;
	}
	
	private static void addHooks(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert, AddressFactory addressFactory) throws YamlConverterException {
		Map<String, Map<String, List<Map<String, String>>>> hookMap = (Map<String, Map<String, List<Map<String, String>>>>) traceFileToConvert
				.get(MorionInitTraceFile.HOOKS);
		Set<Hook> hooks = mapToHooks(hookMap, addressFactory);
		traceFile.getHooks().replaceAll(hooks);
	}
	
	private static Set<Hook> mapToHooks(Map<String, Map<String, List<Map<String, String>>>> hookMap, AddressFactory addressFactory) throws YamlConverterException {
		Set<Hook> hooks = new HashSet<>();
		Map<String, List<Map<String, String>>> functions = hookMap.get("libc"); // Libc is hardcoded for now
		for (String functionName : functions.keySet()) {
			for (Map<String, String> hookDetails : functions.get(functionName)) {
				Address entry = getHookEntryAddress(functionName, hookDetails, addressFactory);
				Mode mode = getHookMode(functionName, hookDetails, entry);

				try {
					hooks.add(new Hook(functionName, entry, mode));
				} catch (NullPointerException e) {
					String title = "Illegal hook entry";
					String message = "Hook entry address " + entry + " is illegal"
							+ " (Function: " + functionName + ")";
					throw new YamlConverterException(title, message, e);
				}
			}
		}
		return hooks;
	}
	
	private static Address getHookEntryAddress(String functionName, Map<String, String> hookDetails, AddressFactory addressFactory) throws YamlConverterException {
		if (! (hookDetails.containsKey(MorionInitTraceFile.HOOK_ENTRY))) {
			String message = "Hook entry address is missing (Function: " + functionName + ")";
			throw new YamlConverterException("Entry missing", message, null);
		}
		String entry = hookDetails.get(MorionInitTraceFile.HOOK_ENTRY);
		return addressFactory.getAddress(entry);
	}
	
	private static Mode getHookMode(String functionName, Map<String, String> hookDetails, Address entry) throws YamlConverterException {
		if (! (hookDetails.containsKey(MorionInitTraceFile.HOOK_MODE))) {
			String message = "Hook mode is missing (Function: " + functionName + ", Entry: " + entry + ")";
			throw new YamlConverterException("Mode missing", message, null);
		}
		Mode mode;
		try {
			mode = Mode.fromValue(hookDetails.get(MorionInitTraceFile.HOOK_MODE));
		} catch (IllegalArgumentException e) {
			String message = "Hook mode " + hookDetails.get(MorionInitTraceFile.HOOK_MODE) + " is illegal" 
					+ " (Function: " + functionName + ", Entry: " + entry + ")";
			throw new YamlConverterException("Illegal hook mode", message, e);
		}
		return mode;
	}
	
	private static void addEntryMemory(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert) throws YamlConverterException {
		Map<String, Map<String, List<String>>> entryStateMap = getEntryStateMap(traceFileToConvert);
		if (entryStateMap.containsKey(MorionInitTraceFile.STATE_MEMORY)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(entryStateMap.get(MorionInitTraceFile.STATE_MEMORY));
			checkMemoryStateAddresses(memoryEntries);
			traceFile.getEntryMemory().replaceAll(memoryEntries);
		}
	}
	
	private static void addEntryRegisters(MorionInitTraceFile traceFile, Map<String, Object> traceFileToConvert) throws YamlConverterException {
		Map<String, Map<String, List<String>>> entryStateMap = getEntryStateMap(traceFileToConvert);
		if (entryStateMap.containsKey(MorionInitTraceFile.STATE_REGISTERS)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(entryStateMap.get(MorionInitTraceFile.STATE_REGISTERS));
			traceFile.getEntryRegisters().replaceAll(memoryEntries);
		}
	}
	
	private static void addLeaveMemory(MorionTraceFile traceFile, Map<String, Object> traceFileToConvert) throws YamlConverterException {
		Map<String, Map<String, List<String>>> leaveStateMap = getLeaveStateMap(traceFileToConvert);
		if (leaveStateMap.containsKey(MorionInitTraceFile.STATE_MEMORY)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(leaveStateMap.get(MorionInitTraceFile.STATE_MEMORY));
			checkMemoryStateAddresses(memoryEntries);
			traceFile.getLeaveMemory().replaceAll(memoryEntries);
		}
	}
	
	private static void addLeaveRegisters(MorionTraceFile traceFile, Map<String, Object> traceFileToConvert) throws YamlConverterException {
		Map<String, Map<String, List<String>>> leaveStateMap = getLeaveStateMap(traceFileToConvert);
		if (leaveStateMap.containsKey(MorionInitTraceFile.STATE_REGISTERS)) {
			List<MemoryEntry> memoryEntries = mapToMemoryEntries(leaveStateMap.get(MorionInitTraceFile.STATE_REGISTERS));
			traceFile.getLeaveRegisters().replaceAll(memoryEntries);
		}
	}
	
	private static void checkMemoryStateAddresses(List<MemoryEntry> memoryEntries) throws YamlConverterException {
		for (MemoryEntry entry : memoryEntries) {
			if (! HexDocument.isValidHex(entry.getName())) {
				String message = "Memory state address '" + entry.getName() + "' has to be hexadecimal";
				throw new YamlConverterException("Illegal memory state address", message, null);
			}
		}
	}

	private static List<MemoryEntry> mapToMemoryEntries(Map<String, List<String>> entryMap) throws YamlConverterException {
		List<MemoryEntry> entries = new ArrayList<>();
		for (String name : entryMap.keySet()) {
			List<String> details = entryMap.get(name);
			if (details == null || details.size() <= 0) {
				String message = "State " + name + " has no value";
				throw new YamlConverterException("Missing state value", message, null);
			}
			String value = details.get(0);
			if (! HexDocument.isValidHex(value)) {
				String message = "State " + name + "'s value has to be hexadecimal";
				throw new YamlConverterException("Illegal state value", message, null);
			}
			boolean symbolic = details.size() > 1 
					&& MorionInitTraceFile.SYMBOLIC.equals(details.get(1));
			entries.add(new MemoryEntry(name, value, symbolic));
		}
		return entries;
	}
	
	private static Map<String, Map<String, List<String>>> getEntryStateMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, List<String>>> entryStateMap = new HashMap<>();
		Map<String, Map<String, Map<String, List<String>>>> statesMap = getStatesMap(traceFileToConvert);
		if (statesMap.containsKey(MorionInitTraceFile.ENTRY_STATE)) {
			entryStateMap = statesMap.get(MorionInitTraceFile.ENTRY_STATE);
		}
		return entryStateMap;
	}
	
	private static Map<String, Map<String, List<String>>> getLeaveStateMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, List<String>>> leaveStateMap = new HashMap<>();
		Map<String, Map<String, Map<String, List<String>>>> statesMap = getStatesMap(traceFileToConvert);
		if (statesMap != null && statesMap.containsKey(MorionInitTraceFile.LEAVE_STATE)) {
			leaveStateMap = statesMap.get(MorionInitTraceFile.LEAVE_STATE);
		}
		return leaveStateMap;
	}
	
	private static Map<String, Map<String, Map<String, List<String>>>> getStatesMap(Map<String, Object> traceFileToConvert) {
		Map<String, Map<String, Map<String, List<String>>>> statesMap = new HashMap<>();
		if (traceFileToConvert.containsKey(MorionInitTraceFile.STATES)) {
			statesMap = (Map<String, Map<String, Map<String, List<String>>>>) traceFileToConvert.get(MorionInitTraceFile.STATES);
		}
		return statesMap;
	}

}
