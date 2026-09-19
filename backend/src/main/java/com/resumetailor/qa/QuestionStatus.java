package com.resumetailor.qa;

/** Every question is answered synchronously, so there is no PENDING/RUNNING state to model. */
public enum QuestionStatus {
    COMPLETED,
    FAILED
}
