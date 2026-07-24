package com.mmm.sync;

enum SyncLifecycleState
{
    DISABLED,
    NOT_AUTHENTICATED,
    IDLE,
    SCHEDULED,
    QUEUED,
    PREPARING,
    UPLOADING,
    WAITING_FOR_RESPONSE,
    SUCCESSFUL,
    RETRY_SCHEDULED,
    FAILED
}
