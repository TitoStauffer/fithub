package com.fithub.training.application.usecase;

import com.fithub.training.domain.aggregate.WorkoutExecution;
import com.fithub.training.domain.service.TrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecordWorkoutExecutionUseCase {
    private final TrainingService trainingService;

    public RecordWorkoutExecutionUseCase(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Transactional
    public RecordExecutionResponse execute(RecordExecutionRequest request) {
        WorkoutExecution execution = trainingService.recordExecution(
            UUID.fromString(request.getPatientId()),
            UUID.fromString(request.getExerciseId()),
            request.getDayOfWeek(),
            request.getActualSets(),
            request.getActualReps(),
            request.getActualWeight(),
            request.getFeeling()
        );

        return new RecordExecutionResponse(execution.getId().toString(), "Execution recorded");
    }

    public static class RecordExecutionRequest {
        private String patientId;
        private String exerciseId;
        private String dayOfWeek;
        private Integer actualSets;
        private Integer actualReps;
        private Double actualWeight;
        private String feeling;

        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public String getExerciseId() { return exerciseId; }
        public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public Integer getActualSets() { return actualSets; }
        public void setActualSets(Integer actualSets) { this.actualSets = actualSets; }
        public Integer getActualReps() { return actualReps; }
        public void setActualReps(Integer actualReps) { this.actualReps = actualReps; }
        public Double getActualWeight() { return actualWeight; }
        public void setActualWeight(Double actualWeight) { this.actualWeight = actualWeight; }
        public String getFeeling() { return feeling; }
        public void setFeeling(String feeling) { this.feeling = feeling; }
    }

    public static class RecordExecutionResponse {
        private String executionId;
        private String message;

        public RecordExecutionResponse(String executionId, String message) {
            this.executionId = executionId;
            this.message = message;
        }

        public String getExecutionId() { return executionId; }
        public String getMessage() { return message; }
    }
}
