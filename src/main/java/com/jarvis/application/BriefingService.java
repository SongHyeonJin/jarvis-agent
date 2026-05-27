package com.jarvis.application;

import com.jarvis.domain.model.Schedule;
import com.jarvis.domain.model.Todo;
import com.jarvis.domain.port.out.ScheduleRepository;
import com.jarvis.domain.port.out.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BriefingService {

    private final TodoRepository todoRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getDailyBriefing() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Todo> todos = todoRepository.findAll().stream()
                .filter(t -> !t.isDone())
                .toList();

        List<Schedule> schedules = scheduleRepository.findByStartTimeBetween(startOfDay, endOfDay);

        String weather = fetchWeather();

        Map<String, Object> result = new HashMap<>();
        result.put("date", today.toString());
        result.put("weather", weather);
        result.put("todos", todos);
        result.put("schedules", schedules);
        return result;
    }

    private String fetchWeather() {
        try {
            // format: "+27°C Partly cloudy" or "-3°C Overcast"
            String raw = WebClient.create("https://wttr.in")
                    .get()
                    .uri("/Seoul?format=%t+%C")
                    .header("User-Agent", "curl/7.68.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return toKoreanWeather(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String toKoreanWeather(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        // Extract numeric temperature: optional sign + digits
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([+-]?\\d+)").matcher(raw);
        String tempNum = m.find() ? m.group(1) : "?";
        String desc = raw.replaceAll("[+-]?\\d+°C\\s*", "").trim().toLowerCase();

        String sky = switch (desc) {
            case "sunny", "clear", "clear sky" -> "맑겠습니다";
            case "partly cloudy", "mostly clear" -> "대체로 맑겠습니다";
            case "cloudy" -> "흐리겠습니다";
            case "overcast" -> "매우 흐리겠습니다";
            case "mist", "fog", "freezing fog" -> "안개가 끼겠습니다";
            case "light rain", "patchy light rain", "patchy rain possible",
                 "drizzle", "light drizzle", "freezing drizzle" -> "가벼운 비가 예상됩니다";
            case "moderate rain", "rain", "moderate or heavy rain shower" -> "비가 올 예정입니다";
            case "heavy rain", "torrential rain shower" -> "많은 비가 예상됩니다";
            case "thunderstorm", "patchy light rain with thunder",
                 "moderate or heavy rain with thunder" -> "천둥번개가 예상됩니다";
            case "light snow", "patchy snow possible" -> "가벼운 눈이 올 수 있습니다";
            case "snow", "moderate snow", "heavy snow", "blizzard" -> "눈이 올 예정입니다";
            default -> desc.isEmpty() ? "맑겠습니다" : desc;
        };

        boolean hasRain = desc.contains("rain") || desc.contains("drizzle") || desc.contains("thunder");
        String rain = hasRain ? " 우산을 챙기세요." : "";
        return tempNum + "도 " + sky + rain;
    }
}
