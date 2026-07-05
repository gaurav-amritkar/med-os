package com.medos.dto;

import lombok.Data;

@Data
public class AiSuggestRequest {
    private String diseaseDescription;
    private String chiefComplaint;
}
