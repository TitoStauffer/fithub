package com.fithub.patients.interfaces.dto;

/**
 * DTO de resposta para listar pacientes de um profissional
 */
public class PatientListResponse {
    private String id;
    private String name;
    private Double weight;
    private Integer age;
    private Double height;
    private Double imc;
    private String status;

    public PatientListResponse(String id, String name, Double weight, Integer age, Double height, Double imc, String status) {
        this.id = id;
        this.name = name;
        this.weight = weight;
        this.age = age;
        this.height = height;
        this.imc = imc;
        this.status = status;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer getAge() {
        return age;
    }

    public Double getHeight() {
        return height;
    }

    public Double getImc() {
        return imc;
    }

    public String getStatus() {
        return status;
    }
}
