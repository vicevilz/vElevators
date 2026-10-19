package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.velevators.model.AuditRecord;
import net.velevators.model.FloorLocation;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransactionLogger {
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(MADRID);
    private static final DateTimeFormatter PARSER = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm 'Europe/Madrid'");
    private final Path pendingFile;
    private final Path permanentFile;

    public TransactionLogger(Path pendingFile) {
        this(pendingFile, pendingFile.resolveSibling("transactions_logs.log"));
    }

    public TransactionLogger(Path pendingFile, Path permanentFile) {
        this.pendingFile = pendingFile;
        this.permanentFile = permanentFile;
    }

    public synchronized void append(Instant instant, String playerName, UUID playerId, BigDecimal amount,
                                    FloorLocation location) throws IOException {
        createParent(pendingFile);
        String line = timestamp(instant) + " | " + safe(playerName) + " | " + playerId
                + " | " + amount.toPlainString() + " | " + safe(location.worldName()) + " | "
                + location.x() + " " + location.y() + " " + location.z() + System.lineSeparator();
        Files.writeString(pendingFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public synchronized Snapshot snapshot() throws IOException {
        return readSnapshot();
    }

    public synchronized CollectResult collect(Economy economy, Player player, Instant instant) {
        Snapshot snapshot;
        try {
            snapshot = readSnapshot();
        } catch (IOException exception) {
            return new CollectResult(CollectStatus.STORAGE_FAILURE, BigDecimal.ZERO, 0, exception.getMessage());
        }
        if (snapshot.rawLineCount() == 0) {
            return new CollectResult(CollectStatus.EMPTY, BigDecimal.ZERO, 0, "");
        }
        if (!snapshot.invalidLines().isEmpty() || snapshot.total().signum() <= 0
                || !Double.isFinite(snapshot.total().doubleValue())) {
            return new CollectResult(CollectStatus.INVALID_LOG, snapshot.total(), snapshot.rawLineCount(), "");
        }

        double amount = snapshot.total().doubleValue();
        EconomyResponse deposit = economy.depositPlayer(player, amount);
        if (!deposit.transactionSuccess()) {
            return new CollectResult(CollectStatus.ECONOMY_FAILURE, snapshot.total(), snapshot.rawLineCount(),
                    deposit.errorMessage == null ? "" : deposit.errorMessage);
        }

        try {
            archiveAndClear(snapshot, ArchiveAction.COLLECT, instant, player.getName(), player.getUniqueId());
            return new CollectResult(CollectStatus.SUCCESS, snapshot.total(), snapshot.rawLineCount(), "");
        } catch (IOException exception) {
            EconomyResponse rollback = economy.withdrawPlayer(player, amount);
            CollectStatus status = rollback.transactionSuccess()
                    ? CollectStatus.STORAGE_FAILURE : CollectStatus.ROLLBACK_FAILURE;
            String rollbackError = rollback.errorMessage == null ? "" : rollback.errorMessage;
            return new CollectResult(status, snapshot.total(), snapshot.rawLineCount(),
                    exception.getMessage() + (rollbackError.isBlank() ? "" : " | " + rollbackError));
        }
    }

    public synchronized ClearResult clear(String actorName, UUID actorId, Instant instant) throws IOException {
        Snapshot snapshot = readSnapshot();
        archiveAndClear(snapshot, ArchiveAction.CLEAR, instant, actorName, actorId);
        return new ClearResult(snapshot.rawLineCount(), snapshot.total(), snapshot.invalidLines().size());
    }

    public synchronized List<AuditRecord> permanentAuditRecords() throws IOException {
        if (!Files.exists(permanentFile)) {
            return List.of();
        }
        List<String> lines = Files.readAllLines(permanentFile, StandardCharsets.UTF_8);
        List<AuditRecord> records = new ArrayList<>();
        for (int index = lines.size() - 1; index >= 0; index--) {
            AuditRecord record = parsePermanentLine(index + 1L, lines.get(index));
            if (record != null) {
                records.add(record);
            }
        }
        return List.copyOf(records);
    }

    public Path pendingFile() {
        return pendingFile;
    }

    public Path permanentFile() {
        return permanentFile;
    }

    private Snapshot readSnapshot() throws IOException {
        if (!Files.exists(pendingFile)) {
            return new Snapshot(List.of(), List.of(), List.of(), BigDecimal.ZERO);
        }
        List<String> rawLines = Files.readAllLines(pendingFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<TransactionRecord> records = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String line : rawLines) {
            TransactionRecord record = parsePendingLine(line);
            if (record == null) {
                invalid.add(line);
                continue;
            }
            records.add(record);
            total = total.add(record.amount());
        }
        return new Snapshot(records, rawLines, invalid, total);
    }

    private TransactionRecord parsePendingLine(String line) {
        String[] fields = split(line);
        if (fields.length != 6) {
            return null;
        }
        try {
            Instant instant = parseTimestamp(fields[0]);
            UUID playerId = UUID.fromString(fields[2]);
            BigDecimal amount = new BigDecimal(fields[3]);
            String[] coordinates = fields[5].trim().split("\\s+");
            if (amount.signum() <= 0 || coordinates.length != 3) {
                return null;
            }
            return new TransactionRecord(fields[0], instant, fields[1], playerId, amount, fields[4],
                    Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                    Integer.parseInt(coordinates[2]), line);
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return null;
        }
    }

    private AuditRecord parsePermanentLine(long id, String line) {
        String[] fields = split(line);
        try {
            return switch (fields[0]) {
                case "PURCHASE" -> fields.length == 7
                        ? new AuditRecord(id, null, UUID.fromString(fields[3]), fields[2],
                        "TRANSACTION_PURCHASE", "Importe: " + fields[4] + " | Mundo: " + fields[5]
                        + " | Coordenadas: " + fields[6] + " | UUID: " + fields[3],
                        parseTimestamp(fields[1]).toString()) : null;
                case "COLLECT" -> fields.length == 6
                        ? new AuditRecord(id, null, UUID.fromString(fields[3]), fields[2],
                        "TRANSACTIONS_COLLECTED", "Retiró " + fields[4] + " de " + fields[5]
                        + " pagos pendientes.", parseTimestamp(fields[1]).toString()) : null;
                case "CLEAR" -> fields.length == 5
                        ? new AuditRecord(id, null, UUID.fromString(fields[3]), fields[2],
                        "TRANSACTIONS_CLEARED", "Archivó " + fields[4]
                        + " pagos sin entregar dinero.", parseTimestamp(fields[1]).toString()) : null;
                default -> null;
            };
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return null;
        }
    }

    private void archiveAndClear(Snapshot snapshot, ArchiveAction action, Instant instant,
                                 String actorName, UUID actorId) throws IOException {
        String previousArchive = Files.exists(permanentFile)
                ? Files.readString(permanentFile, StandardCharsets.UTF_8) : "";
        String previousPending = Files.exists(pendingFile)
                ? Files.readString(pendingFile, StandardCharsets.UTF_8) : "";
        StringBuilder archived = new StringBuilder(previousArchive);
        if (!archived.isEmpty() && archived.charAt(archived.length() - 1) != '\n') {
            archived.append(System.lineSeparator());
        }
        for (String line : snapshot.rawLines()) {
            archived.append(parsePendingLine(line) == null ? "UNPARSED | " : "PURCHASE | ")
                    .append(line.replace('\n', '_').replace('\r', '_'))
                    .append(System.lineSeparator());
        }
        archived.append(action.name()).append(" | ").append(timestamp(instant)).append(" | ")
                .append(safe(actorName)).append(" | ").append(actorId).append(" | ");
        if (action == ArchiveAction.COLLECT) {
            archived.append(snapshot.total().toPlainString()).append(" | ");
        }
        archived.append(snapshot.rawLineCount()).append(System.lineSeparator());

        writeAtomically(permanentFile, archived.toString());
        try {
            writeAtomically(pendingFile, "");
        } catch (IOException clearFailure) {
            try {
                writeAtomically(permanentFile, previousArchive);
                writeAtomically(pendingFile, previousPending);
            } catch (IOException rollbackFailure) {
                clearFailure.addSuppressed(rollbackFailure);
            }
            throw clearFailure;
        }
    }

    private void writeAtomically(Path target, String content) throws IOException {
        createParent(target);
        Path parent = target.getParent();
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void createParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String timestamp(Instant instant) {
        return FORMATTER.format(instant) + " Europe/Madrid";
    }

    private Instant parseTimestamp(String value) {
        return LocalDateTime.parse(value, PARSER).atZone(MADRID).toInstant();
    }

    private String[] split(String value) {
        return value.split("\\s*\\|\\s*", -1);
    }

    private String safe(String value) {
        return value.replace('|', '_').replace('\n', '_').replace('\r', '_');
    }

    private enum ArchiveAction {
        COLLECT,
        CLEAR
    }

    public enum CollectStatus {
        SUCCESS,
        EMPTY,
        INVALID_LOG,
        ECONOMY_FAILURE,
        STORAGE_FAILURE,
        ROLLBACK_FAILURE
    }

    public record TransactionRecord(String timestamp, Instant instant, String playerName, UUID playerId,
                                    BigDecimal amount, String worldName, int x, int y, int z, String rawLine) {
    }

    public record Snapshot(List<TransactionRecord> records, List<String> rawLines, List<String> invalidLines,
                           BigDecimal total) {
        public Snapshot {
            records = List.copyOf(records);
            rawLines = List.copyOf(rawLines);
            invalidLines = List.copyOf(invalidLines);
        }

        public int rawLineCount() {
            return rawLines.size();
        }
    }

    public record CollectResult(CollectStatus status, BigDecimal amount, int count, String error) {
    }

    public record ClearResult(int count, BigDecimal total, int invalidCount) {
    }
}
