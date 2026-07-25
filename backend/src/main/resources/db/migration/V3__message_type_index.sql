-- Le type de message est passé de filtre client (sur la seule page affichée) à filtre
-- serveur, et la barre de filtres lit la liste des types distincts en base.
-- L'index sert les deux : parcours d'index sur l'égalité, et surtout un DISTINCT
-- résolu sans lire la table.
CREATE INDEX IF NOT EXISTS idx_pm_message_type ON payment_messages (message_type);
