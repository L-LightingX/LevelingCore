package com.azuredoom.levelingcore.listeners;

import java.util.UUID;

public interface ConstitutionListener {

    void onConstitutionGain(UUID playerId, int intelligence);
}
