package com.sequencescheduler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/schedule", new ScheduleHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Sequence Scheduler is running on http://localhost:" + port);
    }

    private static int resolvePort() {
        String portValue = System.getenv("PORT");
        if (portValue == null || portValue.isBlank()) {
            return 8080;
        }

        try {
            return Integer.parseInt(portValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid PORT value: " + portValue);
        }
    }

    private static final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                path = "/index.html";
            }

            String resourcePath = "/web" + path;
            try (InputStream inputStream = Main.class.getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    byte[] body = inputStream.readAllBytes();
                    sendResponse(exchange, 200, contentType(path), body);
                    return;
                }
            }

            Path filePath = resolveStaticFile(path);
            if (filePath != null) {
                sendResponse(exchange, 200, contentType(path), Files.readAllBytes(filePath));
                return;
            }

            sendResponse(exchange, 404, "text/plain; charset=utf-8", "Not found");
        }
    }

    private static Path resolveStaticFile(String path) {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        Path[] candidates = new Path[] {
                Path.of("src", "web", relativePath),
                Path.of("out", "web", relativePath),
                Path.of("web", relativePath)
        };

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static final class ScheduleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=utf-8",
                        "{\"error\":\"Only POST is allowed.\"}");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                InputData input = InputData.fromJson(requestBody);
                ScheduleResponse response = Scheduler.buildSchedule(input);
                sendResponse(exchange, 200, "application/json; charset=utf-8", response.toJson());
            } catch (IllegalArgumentException ex) {
                String json = "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}";
                sendResponse(exchange, 400, "application/json; charset=utf-8", json);
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body)
            throws IOException {
        sendResponse(exchange, statusCode, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "text/plain; charset=utf-8";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private record TaskInput(String id, String name, int durationDays, List<String> dependencies) {
        TaskInput {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Each task needs an id.");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Task " + id + " needs a name.");
            }
            if (durationDays <= 0) {
                throw new IllegalArgumentException("Task " + id + " must have a duration greater than zero.");
            }
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }

        String toJson() {
            String dependencyJson = dependencies.stream()
                    .map(dependency -> "\"" + escapeJson(dependency) + "\"")
                    .collect(Collectors.joining(","));
            return """
                    {
                      "id":"%s",
                      "name":"%s",
                      "durationDays":%d,
                      "dependencies":[%s]
                    }
                    """.formatted(
                    escapeJson(id),
                    escapeJson(name),
                    durationDays,
                    dependencyJson
            ).trim();
        }
    }

    private record ScheduledTask(String id, String name, int durationDays, List<String> dependencies,
                                 int startDay, int finishDay) {
        String toJson() {
            String dependencyJson = dependencies.stream()
                    .map(dependency -> "\"" + escapeJson(dependency) + "\"")
                    .collect(Collectors.joining(","));
            return """
                    {
                      "id":"%s",
                      "name":"%s",
                      "durationDays":%d,
                      "dependencies":[%s],
                      "startDay":%d,
                      "finishDay":%d
                    }
                    """.formatted(
                    escapeJson(id),
                    escapeJson(name),
                    durationDays,
                    dependencyJson,
                    startDay,
                    finishDay
            ).trim();
        }
    }

    private record InputData(LocalDate projectStartDate, List<TaskInput> tasks) {
        static InputData fromJson(String json) {
            MiniJson.ObjectNode objectNode = MiniJson.parseObject(json);
            String startDateText = objectNode.getRequiredString("projectStartDate");
            List<MiniJson.ValueNode> taskNodes = objectNode.getRequiredArray("tasks");
            if (taskNodes.isEmpty()) {
                throw new IllegalArgumentException("Add at least one task before generating a schedule.");
            }

            List<TaskInput> tasks = new ArrayList<>();
            Set<String> uniqueIds = new HashSet<>();
            for (MiniJson.ValueNode taskNode : taskNodes) {
                MiniJson.ObjectNode taskObject = taskNode.asObject();
                String id = taskObject.getRequiredString("id").trim();
                if (!uniqueIds.add(id)) {
                    throw new IllegalArgumentException("Task ids must be unique. Duplicate id: " + id);
                }

                List<String> dependencies = new ArrayList<>();
                for (MiniJson.ValueNode dependencyNode : taskObject.getOptionalArray("dependencies")) {
                    dependencies.add(dependencyNode.asString());
                }

                tasks.add(new TaskInput(
                        id,
                        taskObject.getRequiredString("name").trim(),
                        taskObject.getRequiredInt("durationDays"),
                        dependencies
                ));
            }

            return new InputData(LocalDate.parse(startDateText), tasks);
        }
    }

    private record ScheduleResponse(LocalDate projectStartDate, int totalDurationDays, List<ScheduledTask> tasks) {
        String toJson() {
            String taskJson = tasks.stream().map(ScheduledTask::toJson).collect(Collectors.joining(","));
            return """
                    {
                      "projectStartDate":"%s",
                      "totalDurationDays":%d,
                      "tasks":[%s]
                    }
                    """.formatted(projectStartDate, totalDurationDays, taskJson).trim();
        }
    }

    private static final class Scheduler {
        private static ScheduleResponse buildSchedule(InputData inputData) {
            Map<String, TaskInput> taskById = inputData.tasks().stream()
                    .collect(Collectors.toMap(TaskInput::id, task -> task));

            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> dependents = new HashMap<>();
            for (TaskInput task : inputData.tasks()) {
                inDegree.put(task.id(), task.dependencies().size());
                for (String dependency : task.dependencies()) {
                    if (!taskById.containsKey(dependency)) {
                        throw new IllegalArgumentException("Task " + task.id() + " depends on missing task " + dependency + ".");
                    }
                    dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(task.id());
                }
            }

            ArrayDeque<String> queue = inputData.tasks().stream()
                    .filter(task -> task.dependencies().isEmpty())
                    .map(TaskInput::id)
                    .sorted()
                    .collect(Collectors.toCollection(ArrayDeque::new));

            List<String> orderedTaskIds = new ArrayList<>();
            while (!queue.isEmpty()) {
                String taskId = queue.removeFirst();
                orderedTaskIds.add(taskId);
                for (String dependentId : dependents.getOrDefault(taskId, List.of())) {
                    int nextDegree = inDegree.computeIfPresent(dependentId, (ignored, value) -> value - 1);
                    if (nextDegree == 0) {
                        queue.add(dependentId);
                    }
                }
            }

            if (orderedTaskIds.size() != inputData.tasks().size()) {
                throw new IllegalArgumentException("The task graph contains a cycle. Remove circular dependencies.");
            }

            Map<String, ScheduledTask> scheduledById = new HashMap<>();
            for (String taskId : orderedTaskIds) {
                TaskInput task = taskById.get(taskId);
                int startDay = 0;
                for (String dependency : task.dependencies()) {
                    ScheduledTask dependencyTask = Objects.requireNonNull(scheduledById.get(dependency));
                    startDay = Math.max(startDay, dependencyTask.finishDay());
                }
                int finishDay = startDay + task.durationDays();
                scheduledById.put(taskId, new ScheduledTask(
                        task.id(),
                        task.name(),
                        task.durationDays(),
                        task.dependencies(),
                        startDay,
                        finishDay
                ));
            }

            List<ScheduledTask> orderedTasks = scheduledById.values().stream()
                    .sorted(Comparator.comparingInt(ScheduledTask::startDay).thenComparing(ScheduledTask::id))
                    .toList();

            int totalDurationDays = orderedTasks.stream().mapToInt(ScheduledTask::finishDay).max().orElse(0);
            return new ScheduleResponse(inputData.projectStartDate(), totalDurationDays, orderedTasks);
        }
    }

    private static final class MiniJson {
        static ObjectNode parseObject(String json) {
            Parser parser = new Parser(json);
            ValueNode value = parser.parseValue();
            parser.skipWhitespace();
            if (!parser.isFinished()) {
                throw new IllegalArgumentException("Unexpected trailing content in request body.");
            }
            return value.asObject();
        }

        private record ValueNode(Object value) {
            ObjectNode asObject() {
                if (value instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, ValueNode> typed = (Map<String, ValueNode>) map;
                    return new ObjectNode(typed);
                }
                throw new IllegalArgumentException("Expected a JSON object.");
            }

            String asString() {
                if (value instanceof String text) {
                    return text;
                }
                throw new IllegalArgumentException("Expected a JSON string.");
            }

            int asInt() {
                if (value instanceof Integer number) {
                    return number;
                }
                throw new IllegalArgumentException("Expected a JSON integer.");
            }

            List<ValueNode> asArray() {
                if (value instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<ValueNode> typed = (List<ValueNode>) list;
                    return typed;
                }
                throw new IllegalArgumentException("Expected a JSON array.");
            }
        }

        private record ObjectNode(Map<String, ValueNode> values) {
            String getRequiredString(String key) {
                return getRequired(key).asString();
            }

            int getRequiredInt(String key) {
                return getRequired(key).asInt();
            }

            List<ValueNode> getRequiredArray(String key) {
                return getRequired(key).asArray();
            }

            List<ValueNode> getOptionalArray(String key) {
                ValueNode node = values.get(key);
                return node == null ? List.of() : node.asArray();
            }

            private ValueNode getRequired(String key) {
                ValueNode node = values.get(key);
                if (node == null) {
                    throw new IllegalArgumentException("Missing required field: " + key);
                }
                return node;
            }
        }

        private static final class Parser {
            private final String text;
            private int index;

            private Parser(String text) {
                this.text = text;
            }

            private boolean isFinished() {
                return index >= text.length();
            }

            private void skipWhitespace() {
                while (!isFinished() && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
            }

            private ValueNode parseValue() {
                skipWhitespace();
                if (isFinished()) {
                    throw new IllegalArgumentException("Request body is empty.");
                }

                char token = text.charAt(index);
                if (token == '{') {
                    return new ValueNode(parseObjectValue());
                }
                if (token == '[') {
                    return new ValueNode(parseArrayValue());
                }
                if (token == '"') {
                    return new ValueNode(parseString());
                }
                if (token == '-' || Character.isDigit(token)) {
                    return new ValueNode(parseInt());
                }
                throw new IllegalArgumentException("Unsupported JSON token near position " + index + ".");
            }

            private Map<String, ValueNode> parseObjectValue() {
                expect('{');
                skipWhitespace();
                Map<String, ValueNode> object = new HashMap<>();
                if (peek('}')) {
                    expect('}');
                    return object;
                }

                while (true) {
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    ValueNode value = parseValue();
                    object.put(key, value);
                    skipWhitespace();
                    if (peek('}')) {
                        expect('}');
                        return object;
                    }
                    expect(',');
                }
            }

            private List<ValueNode> parseArrayValue() {
                expect('[');
                skipWhitespace();
                List<ValueNode> array = new ArrayList<>();
                if (peek(']')) {
                    expect(']');
                    return array;
                }

                while (true) {
                    array.add(parseValue());
                    skipWhitespace();
                    if (peek(']')) {
                        expect(']');
                        return array;
                    }
                    expect(',');
                }
            }

            private String parseString() {
                expect('"');
                StringBuilder builder = new StringBuilder();
                while (!isFinished()) {
                    char current = text.charAt(index++);
                    if (current == '"') {
                        return builder.toString();
                    }
                    if (current == '\\') {
                        if (isFinished()) {
                            throw new IllegalArgumentException("Invalid escape sequence.");
                        }
                        char escaped = text.charAt(index++);
                        builder.append(switch (escaped) {
                            case '"', '\\', '/' -> escaped;
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> throw new IllegalArgumentException("Unsupported escape sequence: \\" + escaped);
                        });
                    } else {
                        builder.append(current);
                    }
                }
                throw new IllegalArgumentException("Unterminated string.");
            }

            private int parseInt() {
                int start = index;
                if (text.charAt(index) == '-') {
                    index++;
                }
                while (!isFinished() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
                return Integer.parseInt(text.substring(start, index));
            }

            private boolean peek(char value) {
                return !isFinished() && text.charAt(index) == value;
            }

            private void expect(char value) {
                skipWhitespace();
                if (isFinished() || text.charAt(index) != value) {
                    throw new IllegalArgumentException("Expected '" + value + "' near position " + index + ".");
                }
                index++;
            }
        }
    }
}
