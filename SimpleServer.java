import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleServer {
    private static final Map<Long, Todo> todos = new ConcurrentHashMap<>();
    private static final AtomicLong idCounter = new AtomicLong(0);
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // REST API endpoints
        server.createContext("/api/todos", new TodosHandler());
        
        // Static HTML page
        server.createContext("/", new StaticHandler());
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server running on port 8080");
    }
    
    static class Todo {
        Long id;
        String task;
        boolean completed;
        
        public Todo() {}
        
        public Todo(Long id, String task) {
            this.id = id;
            this.task = task;
            this.completed = false;
        }
    }
    
    static class TodosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            String path = exchange.getRequestURI().getPath();
            
            // Parse ID path parameter
            if (path.matches("/api/todos/\\d+$")) {
                String[] parts = path.split("/");
                params.put("id", parts[parts.length - 1]);
            }
            
            String method = exchange.getRequestMethod();
            String response = "";
            int status;
            
            try {
                switch(method) {
                    case "GET":
                        if (params.containsKey("id")) {
                            response = getTodo(Long.parseLong(params.get("id")));
                        } else {
                            response = getAllTodos();
                        }
                        status = 200;
                        break;
                    case "POST":
                        response = createTodo(InputStreamToString(exchange.getRequestBody()));
                        status = 201;
                        break;
                    case "PUT":
                        response = updateTodo(Long.parseLong(params.get("id")), 
                                             InputStreamToString(exchange.getRequestBody()));
                        status = 200;
                        break;
                    case "DELETE":
                        deleteTodo(Long.parseLong(params.get("id")));
                        status = 204;
                        break;
                    default:
                        response = "Method not allowed";
                        status = 405;
                }
            } catch (Exception e) {
                response = "Error: " + e.getMessage();
                status = 400;
            }
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
        
        private String InputStreamToString(InputStream is) {
            try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
        
        private String getTodo(Long id) {
            return todos.containsKey(id) ? 
                "{\"id\":" + id + ",\"task\":\"" + todos.get(id).task + "\",\"completed\":" + todos.get(id).completed + "}" 
                : "{}";
        }
        
        private String getAllTodos() {
            StringBuilder sb = new StringBuilder("[");
            todos.forEach((id, todo) -> {
                sb.append("{\"id\":").append(id)
                  .append(",\"task\":\"").append(todo.task)
                  .append("\",\"completed\":").append(todo.completed).append("},");
            });
            if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
            return sb.append("]").toString();
        }
        
        private String createTodo(String body) {
            String task = body.substring(6, body.length()); // Extracts content after "task="
            Long id = idCounter.incrementAndGet();
            todos.put(id, new Todo(id, task));
            return "{\"id\":" + id + "}";
        }
        
        private String updateTodo(Long id, String body) {
            if (!todos.containsKey(id)) throw new RuntimeException("Todo not found");
            
            if (body.contains("=")) {
                String value = body.split("=")[1];
                if (body.startsWith("task")) {
                    todos.get(id).task = value;
                } else if (body.startsWith("completed")) {
                    todos.get(id).completed = Boolean.parseBoolean(value);
                }
            }
            return "{\"success\":true}";
        }
        
        private void deleteTodo(Long id) {
            todos.remove(id);
        }
    }
    
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String htmlPage = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<title>Todo App</title>\n" +
                "<style>\n" +
                "  body { font-family: sans-serif; max-width: 600px; margin: auto; padding: 20px; }\n" +
                "  h1 { color: #333; }\n" +
                "  #todos { margin: 20px 0; }\n" +
                "  .todo-item { display: flex; justify-content: space-between; padding: 10px; border-bottom: 1px solid #eee; }\n" +
                "  button { cursor: pointer; }\n" +
                "</style>\n" +
                "<script>\n" +
                "  function fetchTodos() {\n" +
                "    fetch('/api/todos')\n" +
                "      .then(res => res.json())\n" +
                "      .then(todos => {\n" +
                "        const container = document.getElementById('todos');\n" +
                "        container.innerHTML = '';\n" +
                "        todos.forEach(todo => {\n" +
                "          const div = document.createElement('div');\n" +
                "          div.className = 'todo-item';\n" +
                "          div.innerHTML = `\n" +
                "            <div>\n" +
                "              <input type='checkbox' ${todo.completed ? 'checked' : ''} \n" +
                "                onclick='toggleTodo(${todo.id}, this.checked)'>\n" +
                "              <span>${todo.task}</span>\n" +
                "            </div>\n" +
                "            <button onclick='deleteTodo(${todo.id})'>Delete</button>`;\n" +
                "          container.appendChild(div);\n" +
                "        });\n" +
                "      });\n" +
                "  }\n" +
                "  \n" +
                "  function createTodo() {\n" +
                "    const input = document.getElementById('new-todo');\n" +
                "    fetch('/api/todos', {\n" +
                "      method: 'POST',\n" +
                "      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
                "      body: 'task=' + input.value\n" +
                "    }).then(() => {\n" +
                "      input.value = '';\n" +
                "      fetchTodos();\n" +
                "    });\n" +
                "  }\n" +
                "  \n" +
                "  function toggleTodo(id, completed) {\n" +
                "    fetch('/api/todos/' + id, {\n" +
                "      method: 'PUT',\n" +
                "      body: 'completed=' + completed\n" +
                "    });\n" +
                "  }\n" +
                "  \n" +
                "  function deleteTodo(id) {\n" +
                "    fetch('/api/todos/' + id, {\n" +
                "      method: 'DELETE'\n" +
                "    }).then(fetchTodos);\n" +
                "  }\n" +
                "  \n" +
                "  // Initialize\n" +
                "  document.addEventListener('DOMContentLoaded', fetchTodos);\n" +
                "</script>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <h1>Todo App</h1>\n" +
                "  <div>\n" +
                "    <input id='new-todo' type='text' placeholder='New todo' />\n" +
                "    <button onclick='createTodo()'>Add</button>\n" +
                "  </div>\n" +
                "  <div id='todos'></div>\n" +
                "</body>\n" +
                "</html>";
            
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, htmlPage.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(htmlPage.getBytes());
            os.close();
        }
    }
}
