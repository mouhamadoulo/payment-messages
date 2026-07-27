/**
 * Modèles de payload proposés par la simulation.
 *
 * Le contrat est celui de la file d'entrée (`dto/mq/PaymentMessageEvent` côté serveur) et non
 * celui de l'API REST : `messageId`, `messageType`, `reference`, `payment`, `status` sont
 * obligatoires. Deux pièges de sérialisation s'y cachent, d'où les valeurs littérales ci-dessous :
 * `payment.executionDate` est une `LocalDate` (`AAAA-MM-JJ`) et `createdAt` une `LocalDateTime`
 * — **sans** décalage horaire, contrairement aux horodatages de l'API.
 *
 * Les deux derniers modèles sont invalides à dessein : ils couvrent les deux chemins de rejet
 * définitif du consommateur — JSON illisible et validation en échec — qui persistent l'un
 * comme l'autre une ligne `FAILED` porteuse du payload brut.
 */
export interface PayloadTemplate {
  key: string;
  name: string;
  description: string;
  /** Le message sera rejeté par le consommateur : l'IHM le signale avant l'envoi. */
  invalid: boolean;
  build: (now: Date) => string;
}

export const PAYLOAD_TEMPLATES: PayloadTemplate[] = [
  {
    key: 'sepa',
    name: 'Virement SEPA',
    description: 'Virement de compte à compte, cas nominal',
    invalid: false,
    build: (now) => json({
      messageId: messageId('SEPA', now),
      messageType: 'SEPA_CREDIT_TRANSFER',
      reference: reference('VIR', now),
      payment: {
        transactionId: transactionId(now),
        amount: 15750.00,
        currency: 'EUR',
        executionDate: isoDate(now),
      },
      debtor: {
        accountNumber: 'FR7630006000011234567890189',
        name: 'ACME Corp SA',
        bankCode: 'AGRIFRPP',
      },
      creditor: {
        accountNumber: 'DE89370400440532013000',
        name: 'Vinci Construction',
        bankCode: 'DEUTDEFF',
      },
      status: 'RECEIVED',
      createdAt: isoLocalDateTime(now),
    }),
  },
  {
    key: 'swift',
    name: 'Virement international',
    description: 'SWIFT MT103, devise étrangère et montant élevé',
    invalid: false,
    build: (now) => json({
      messageId: messageId('SWIFT', now),
      messageType: 'SWIFT_MT103',
      reference: reference('INT', now),
      payment: {
        transactionId: transactionId(now),
        amount: 248900.00,
        currency: 'USD',
        executionDate: isoDate(now),
      },
      debtor: {
        accountNumber: 'FR7612345000099887766554',
        name: 'Airbus Finance',
        bankCode: 'BNPAFRPP',
      },
      creditor: {
        accountNumber: 'US64SVBK00700099887766',
        name: 'Boeing Treasury',
        bankCode: 'SVBKUS6S',
      },
      status: 'RECEIVED',
      createdAt: isoLocalDateTime(now),
    }),
  },
  {
    key: 'direct-debit',
    name: 'Prélèvement SEPA',
    description: 'Petit montant, flux de masse',
    invalid: false,
    build: (now) => json({
      messageId: messageId('DD', now),
      messageType: 'SEPA_DIRECT_DEBIT',
      reference: reference('PRLV', now),
      payment: {
        transactionId: transactionId(now),
        amount: 89.90,
        currency: 'EUR',
        executionDate: isoDate(now),
      },
      debtor: {
        accountNumber: 'FR7610107001010012345678902',
        name: 'Marie Durand',
        bankCode: 'BREDFRPP',
      },
      creditor: {
        accountNumber: 'FR7630004000031234567890143',
        name: 'Orange SA',
        bankCode: 'BNPAFRPP',
      },
      status: 'RECEIVED',
      createdAt: isoLocalDateTime(now),
    }),
  },
  {
    key: 'missing-fields',
    name: 'Champs manquants',
    description: 'JSON lisible mais incomplet — rejet à la validation',
    invalid: true,
    build: (now) => json({
      messageId: messageId('KO', now),
      messageType: 'SEPA_CREDIT_TRANSFER',
      // `reference`, `payment` et `status` sont obligatoires : leur absence déclenche
      // le rejet définitif, sans redélivrance.
      debtor: { accountNumber: 'FR7630006000011234567890189', name: 'ACME Corp SA' },
      createdAt: isoLocalDateTime(now),
    }),
  },
  {
    key: 'malformed',
    name: 'JSON illisible',
    description: 'Payload non désérialisable — rejet à la lecture',
    invalid: true,
    build: () => [
      '{',
      '  messageType: SEPA_CREDIT_TRANSFER,',
      '  amount: 100,00,',
      '  currency "EUR"',
      '  // payload volontairement illisible : teste le rejet à la désérialisation',
      '}',
    ].join('\n'),
  },
];

export const DEFAULT_TEMPLATE = PAYLOAD_TEMPLATES[0];

function json(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

/** `AAAA-MM-JJ` en heure locale — `toISOString()` bascule en UTC et peut changer de jour. */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** `LocalDateTime` côté serveur : pas de décalage horaire, pas de `Z`. */
function isoLocalDateTime(d: Date): string {
  return `${isoDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/**
 * Identifiant lisible et distinct d'une ouverture d'écran à l'autre. Le serveur le réécrit
 * de toute façon à chaque copie d'un envoi en masse (cf. `uniqueIds`) : celui-ci ne sert
 * qu'au message unitaire.
 */
function messageId(prefix: string, now: Date): string {
  return `MSG-${prefix}-${now.getTime().toString(36).toUpperCase()}`;
}

function reference(prefix: string, now: Date): string {
  return `${prefix}-${now.getFullYear()}-${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`;
}

function transactionId(now: Date): string {
  return `TX-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${now.getTime().toString().slice(-6)}`;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}
