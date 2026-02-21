import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, Setting } from '../../core/services/api.service';

type SettingSection = 'schedule' | 'quality' | 'solver' | 'operations' | 'advanced';
type InputType = 'number' | 'time' | 'date' | 'boolean' | 'select' | 'text';

const MOVE_THREAD_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'AUTO', label: 'Auto (choose for me)' },
  ...Array.from({ length: 32 }, (_, i) => {
    const n = String(i + 1);
    const label = i + 1 === 4 ? '4 threads (recommended)' : `${n} threads`;
    return { value: n, label };
  })
];

const PARALLEL_SOLVER_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'AUTO', label: 'Auto (advanced)' },
  ...Array.from({ length: 16 }, (_, i) => {
    const n = String(i + 1);
    const label = i + 1 === 1 ? '1 job (recommended)' : `${n} jobs`;
    return { value: n, label };
  })
];

interface SettingDescriptor {
  label: string;
  help: string;
  section: SettingSection;
  input: InputType;
  placeholder?: string;
  min?: number;
  max?: number;
  step?: number;
  restartRequired?: boolean;
  recommended?: string;
  recommendedMin?: number;
  recommendedMax?: number;
  options?: Array<{ value: string; label: string }>;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);

  settings: Setting[] = [];
  draftValues: Record<string, string> = {};
  savingKeys = new Set<string>();
  globalMessage = '';
  globalSuccess = true;

  regenerating = false;
  regenerateMessage = '';
  regenerateSuccess = true;

  showWipeConfirmation = false;
  wipeConfirmText = '';
  wiping = false;
  wipeMessage = '';
  wipeSuccess = false;

  unavailabilitySettings = { systemEnabled: false, requestsOpen: false };
  unavailabilityMessage = '';
  unavailabilitySuccess = true;

  readonly sections: Array<{ key: SettingSection; title: string; subtitle: string }> = [
    {
      key: 'schedule',
      title: 'Schedule Rules',
      subtitle: 'Define teaching window and guardrails used when building timeslots.'
    },
    {
      key: 'quality',
      title: 'Timetable Quality Preferences',
      subtitle: 'Control soft preferences such as fatigue reduction and day balance.'
    },
    {
      key: 'solver',
      title: 'Solver Speed And Accuracy',
      subtitle: 'Tune solver runtime. Start with Balanced preset, then benchmark before major changes.'
    },
    {
      key: 'operations',
      title: 'Operational Controls',
      subtitle: 'Controls for imports, request workflows and checkpoint persistence.'
    },
    {
      key: 'advanced',
      title: 'Additional Settings',
      subtitle: 'Less common controls. Safe defaults are already applied.'
    }
  ];

  readonly descriptors: Record<string, SettingDescriptor> = {
    lunch_break_start: {
      label: 'Lunch break start',
      help: 'Classes cannot overlap lunch once lunch break enforcement is ON.',
      section: 'schedule',
      input: 'time'
    },
    lunch_break_end: {
      label: 'Lunch break end',
      help: 'End time for lunch block.',
      section: 'schedule',
      input: 'time'
    },
    earliest_start_time: {
      label: 'Earliest class start',
      help: 'First allowed timeslot start in the day.',
      section: 'schedule',
      input: 'time'
    },
    latest_end_time: {
      label: 'Latest class end (Mon-Thu)',
      help: 'Lessons ending later than this are hard-invalid.',
      section: 'schedule',
      input: 'time'
    },
    friday_latest_end_time: {
      label: 'Latest class end (Friday)',
      help: 'Separate end-time rule for Friday.',
      section: 'schedule',
      input: 'time'
    },
    max_lecturer_hours_per_day: {
      label: 'Max lecturer hours per day',
      help: 'Daily hard cap for lecturer teaching load.',
      section: 'schedule',
      input: 'number',
      min: 1,
      max: 12,
      step: 1
    },
    max_student_consecutive_hours: {
      label: 'Max student consecutive hours',
      help: 'Soft fatigue rule for consecutive student lessons.',
      section: 'quality',
      input: 'number',
      min: 1,
      max: 8,
      step: 1
    },
    max_lecturer_consecutive_hours: {
      label: 'Max lecturer consecutive hours',
      help: 'Guideline threshold for lecturer fatigue analysis.',
      section: 'quality',
      input: 'number',
      min: 1,
      max: 8,
      step: 1
    },
    min_break_between_lessons: {
      label: 'Minimum break between lecturer lessons (minutes)',
      help: 'Minimum preferred gap between lecturer classes.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 180,
      step: 5
    },
    weight_room_capacity: {
      label: 'Room capacity efficiency weight',
      help: 'Higher value favors tighter room-size fit and reduces wasted seats.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 20,
      step: 1
    },
    weight_day_balance: {
      label: 'Day balance weight',
      help: 'Higher value spreads same-group lessons across days.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 20,
      step: 1
    },
    weight_lecturer_transition: {
      label: 'Lecturer room transition weight',
      help: 'Higher value reduces consecutive room/zone switching for lecturers.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 20,
      step: 1
    },
    weight_student_fatigue: {
      label: 'Student fatigue weight',
      help: 'Higher value penalizes back-to-back lessons for the same group.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 20,
      step: 1
    },
    weight_early_morning: {
      label: 'Early morning (7am) penalty weight',
      help: 'Higher value avoids scheduling at 07:00 unless necessary.',
      section: 'quality',
      input: 'number',
      min: 0,
      max: 20,
      step: 1
    },
    enforce_lunch_break: {
      label: 'Enforce lunch break',
      help: 'Turns lunch break into a hard blocker.',
      section: 'schedule',
      input: 'boolean'
    },
    enforce_day_balance: {
      label: 'Enforce day balance preference',
      help: 'Apply day-balance soft optimization.',
      section: 'quality',
      input: 'boolean'
    },
    same_course_same_day_allowed: {
      label: 'Allow same course twice in one day',
      help: 'If ON, two parts of the same course can be placed on one day.',
      section: 'schedule',
      input: 'boolean'
    },
    availability_deadline: {
      label: 'Availability submission deadline',
      help: 'Last date lecturers can submit availability. Leave empty if you do not want a deadline.',
      section: 'operations',
      input: 'date',
      placeholder: 'YYYY-MM-DD'
    },
    solver_minutes_spent_limit: {
      label: 'Solver max runtime (minutes)',
      help: 'Hard cap on total solve duration. Turn OFF "Use runtime limit" to allow indefinite solving.',
      section: 'solver',
      input: 'number',
      min: 1,
      recommendedMin: 10,
      recommendedMax: 45,
      step: 1,
      restartRequired: true
    },
    solver_runtime_limit_enabled: {
      label: 'Use runtime limit',
      help: 'When OFF, solver does not stop due to max runtime and can run indefinitely until stopped.',
      section: 'solver',
      input: 'boolean',
      recommended: 'Keep ON for predictable run duration.'
    },
    solver_unimproved_seconds_spent_limit: {
      label: 'Stop after no improvement (seconds)',
      help: 'Stops early if score does not improve for this duration.',
      section: 'solver',
      input: 'number',
      min: 5,
      max: 1800,
      recommendedMin: 20,
      recommendedMax: 120,
      step: 5,
      restartRequired: true
    },
    solver_forager_accepted_count_limit: {
      label: 'Search breadth per step',
      help: 'Moves evaluated per step. Higher explores more but can slow runtime significantly.',
      section: 'solver',
      input: 'number',
      min: 1,
      recommendedMin: 2,
      recommendedMax: 8,
      step: 1,
      restartRequired: true
    },
    solver_move_thread_count: {
      label: 'CPU threads used by solver',
      help: 'Multi-threaded solving requires Timefold Enterprise. Community edition runs single-threaded.',
      section: 'solver',
      input: 'select',
      options: MOVE_THREAD_OPTIONS,
      recommended: 'Multi-threaded solving requires Timefold Enterprise license.',
      restartRequired: true
    },
    solver_environment_mode: {
      label: 'Solver execution mode',
      help: 'NON_REPRODUCIBLE is faster (15-25%). REPRODUCIBLE adds overhead for deterministic results.',
      section: 'solver',
      input: 'select',
      options: [
        { value: 'NON_REPRODUCIBLE', label: 'Non-reproducible (recommended, faster)' },
        { value: 'REPRODUCIBLE', label: 'Reproducible (slower, deterministic)' }
      ],
      restartRequired: true
    },
    solver_parallel_solver_count: {
      label: 'Parallel solve jobs',
      help: 'How many separate solves can run at once. Keep 1 for this system.',
      section: 'solver',
      input: 'select',
      options: PARALLEL_SOLVER_OPTIONS,
      recommendedMin: 1,
      recommendedMax: 4,
      restartRequired: true
    },
    solver_checkpoint_enabled: {
      label: 'Checkpoint intermediate best solutions',
      help: 'ON saves intermediate best results during solve (more DB writes).',
      section: 'operations',
      input: 'boolean'
    },
    solver_checkpoint_min_interval_ms: {
      label: 'Checkpoint min interval (ms)',
      help: 'Minimum delay between checkpoint writes.',
      section: 'operations',
      input: 'number',
      min: 1000,
      max: 900000,
      step: 1000
    },
    solver_checkpoint_every_n_improvements: {
      label: 'Checkpoint every N improvements',
      help: '0 disables this trigger. Use with interval for controlled persistence.',
      section: 'operations',
      input: 'number',
      min: 0,
      max: 1000,
      step: 1
    },
    solver_ruin_recreate_enabled: {
      label: 'Deep restructuring (Ruin & Recreate)',
      help: 'When enabled, the solver detects problem areas — groups of lessons with scheduling conflicts that cannot be fixed by small adjustments — and tears them out to rebuild from scratch. This only activates after the solver has been running for several minutes and is stuck on unresolvable conflicts. Think of it like clearing a tangled section of wiring and re-routing it cleanly. Recommended ON for large timetables (300+ lessons). Default: OFF.',
      section: 'solver',
      input: 'boolean'
    },
    solver_ruin_recreate_cluster_size: {
      label: 'Restructuring group size',
      help: 'How many conflicting lessons the solver pulls out and rebuilds at once. It automatically selects the most problematic lessons — this just caps how many it handles per attempt. Larger groups can resolve bigger tangles but take longer. Recommended: 6-12.',
      section: 'solver',
      input: 'number',
      min: 3,
      max: 25,
      step: 1
    },
    solver_adaptive_limits_enabled: {
      label: 'Use adaptive runtime limits',
      help: 'When ON, solver auto-adjusts max runtime and no-improvement timeout by dataset size. Turn OFF to enforce the exact values you set.',
      section: 'solver',
      input: 'boolean',
      recommended: 'Turn OFF if you want strict manual runtime control.'
    },
    solver_adaptive_search_breadth_enabled: {
      label: 'Use adaptive search breadth',
      help: 'When ON, solver auto-tunes search breadth per step. Turn OFF to use your exact Search breadth per step value.',
      section: 'solver',
      input: 'boolean',
      recommended: 'Turn OFF if you want strict manual search breadth control.'
    },
    bulk_import_rollback_window_hours: {
      label: 'Import rollback window (hours)',
      help: 'How long after import rollback remains allowed. Use -1 for unlimited.',
      section: 'operations',
      input: 'number',
      min: -1,
      max: 168,
      step: 1
    },
    unavailability_system_enabled: {
      label: 'Unavailability system enabled',
      help: 'When OFF, solver ignores lecturer unavailability records.',
      section: 'operations',
      input: 'boolean'
    },
    unavailability_requests_open: {
      label: 'Unavailability requests open',
      help: 'When ON, lecturers can submit requests. Solver is blocked while open.',
      section: 'operations',
      input: 'boolean'
    }
  };

  ngOnInit(): void {
    this.loadAll();
  }

  async loadAll(): Promise<void> {
    await Promise.all([this.loadSettings(), this.loadUnavailabilitySettings()]);
  }

  async loadSettings(): Promise<void> {
    const settings = await firstValueFrom(this.api.getSettings());
    this.settings = settings.sort((a, b) => a.key.localeCompare(b.key));
    this.draftValues = {};
    for (const s of this.settings) {
      const normalizedValue = this.normalizeSelectValue(s, s.value);
      s.value = normalizedValue;
      this.draftValues[s.key] = normalizedValue;
    }
  }

  async loadUnavailabilitySettings(): Promise<void> {
    try {
      this.unavailabilitySettings = await firstValueFrom(this.api.getUnavailabilitySystemSettings());
    } catch {
      this.showUnavailabilityMessage('Failed to load unavailability controls.', false);
    }
  }

  getSectionSettings(section: SettingSection): Setting[] {
    if (section === 'advanced') {
      return this.settings.filter((s) => !this.descriptors[s.key]);
    }
    return this.settings.filter((s) => this.descriptors[s.key]?.section === section);
  }

  descriptorOf(setting: Setting): SettingDescriptor {
    return this.descriptors[setting.key] ?? {
      label: this.formatFallbackLabel(setting.key),
      help: setting.description || 'No description provided.',
      section: 'advanced',
      input: this.inferInputType(setting)
    };
  }

  inferInputType(setting: Setting): InputType {
    const type = (setting.dataType || '').toUpperCase();
    if (type === 'BOOLEAN') return 'boolean';
    if (type === 'TIME') return 'time';
    if (setting.key === 'availability_deadline') return 'date';
    if (type === 'INTEGER') return 'number';
    return 'text';
  }

  isDirty(setting: Setting): boolean {
    return this.draftValues[setting.key] !== setting.value;
  }

  isSaving(settingKey: string): boolean {
    return this.savingKeys.has(settingKey);
  }

  getRecommendedRangeWarning(setting: Setting): string | null {
    const descriptor = this.descriptorOf(setting);
    if (descriptor.section !== 'solver' || descriptor.input !== 'number') {
      return null;
    }

    const rawValue = this.draftValues[setting.key];
    const numericValue = Number(rawValue);
    if (!Number.isFinite(numericValue)) {
      return 'Enter a valid number.';
    }

    const min = descriptor.recommendedMin ?? descriptor.min;
    const max = descriptor.recommendedMax ?? descriptor.max;
    if (min == null || max == null) {
      return null;
    }

    if (numericValue < min || numericValue > max) {
      return `Recommended range: ${min} to ${max}.`;
    }
    return null;
  }

  getValidationError(setting: Setting): string | null {
    return this.getValidationErrorForValue(setting, this.draftValues[setting.key] ?? '');
  }

  isInvalid(setting: Setting): boolean {
    return this.getValidationError(setting) !== null;
  }

  private getValidationErrorForValue(setting: Setting, rawValue: string): string | null {
    const descriptor = this.descriptorOf(setting);
    const value = (rawValue ?? '').trim();

    if (descriptor.input === 'boolean') {
      if (value !== 'true' && value !== 'false') {
        return 'Select On or Off.';
      }
      return null;
    }

    if (descriptor.input === 'select') {
      const allowed = new Set(this.getSelectOptions(setting).map((o) => o.value.trim().toUpperCase()));
      if (!allowed.has(value.toUpperCase())) {
        return 'Please choose a value from the list.';
      }
      return null;
    }

    if (descriptor.input === 'number') {
      if (!/^-?\d+$/.test(value)) {
        return 'Enter a whole number.';
      }
      const num = Number(value);
      if (descriptor.min != null && num < descriptor.min) {
        return `Minimum allowed value is ${descriptor.min}.`;
      }
      if (descriptor.max != null && num > descriptor.max) {
        return `Maximum allowed value is ${descriptor.max}.`;
      }
      return null;
    }

    if (descriptor.input === 'time') {
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) {
        return 'Use 24-hour format HH:mm (example: 13:30).';
      }
      const crossFieldError = this.getTimeCrossFieldError(setting.key, value);
      if (crossFieldError) {
        return crossFieldError;
      }
      return null;
    }

    if (descriptor.input === 'date') {
      if (!value) {
        return null;
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return 'Use date format YYYY-MM-DD.';
      }
      const date = new Date(`${value}T00:00:00`);
      if (Number.isNaN(date.getTime())) {
        return 'Enter a valid date.';
      }
      return null;
    }

    return null;
  }

  private getTimeCrossFieldError(key: string, value: string): string | null {
    const toMinutes = (time: string) => {
      const [h, m] = time.split(':').map(Number);
      return (h * 60) + m;
    };
    const current = toMinutes(value);
    const lunchStart = this.draftValues['lunch_break_start'];
    const lunchEnd = this.draftValues['lunch_break_end'];
    const earliestStart = this.draftValues['earliest_start_time'];
    const latestEnd = this.draftValues['latest_end_time'];

    if (key === 'lunch_break_start' && lunchEnd && current >= toMinutes(lunchEnd)) {
      return 'Lunch break start must be earlier than lunch break end.';
    }
    if (key === 'lunch_break_end' && lunchStart && current <= toMinutes(lunchStart)) {
      return 'Lunch break end must be later than lunch break start.';
    }
    if (key === 'earliest_start_time' && latestEnd && current >= toMinutes(latestEnd)) {
      return 'Earliest class start must be earlier than latest class end.';
    }
    if (key === 'latest_end_time' && earliestStart && current <= toMinutes(earliestStart)) {
      return 'Latest class end must be later than earliest class start.';
    }
    return null;
  }

  private formatFallbackLabel(key: string): string {
    const wordOverrides: Record<string, string> = {
      api: 'API',
      cpu: 'CPU',
      ms: 'ms',
      id: 'ID'
    };
    return key
      .split('_')
      .filter(Boolean)
      .map((word) => {
        const lowered = word.toLowerCase();
        if (wordOverrides[lowered]) {
          return wordOverrides[lowered];
        }
        return lowered.charAt(0).toUpperCase() + lowered.slice(1);
      })
      .join(' ');
  }

  getSelectOptions(setting: Setting): Array<{ value: string; label: string }> {
    const descriptor = this.descriptorOf(setting);
    const options = [...(descriptor.options || [])];
    const current = (this.draftValues[setting.key] ?? '').trim();
    if (!current) {
      return options;
    }

    const hasCurrent = options.some((opt) => this.equalSelectValue(opt.value, current));
    if (!hasCurrent) {
      options.unshift({ value: current, label: `${current} (current value)` });
    }
    return options;
  }

  private normalizeSelectValue(setting: Setting, rawValue: string): string {
    const descriptor = this.descriptorOf(setting);
    if (descriptor.input !== 'select') {
      return rawValue;
    }

    const normalized = (rawValue ?? '').trim();
    if (!normalized) {
      return normalized;
    }

    const matched = (descriptor.options || []).find((opt) => this.equalSelectValue(opt.value, normalized));
    return matched ? matched.value : normalized;
  }

  private equalSelectValue(optionValue: string, currentValue: string): boolean {
    return optionValue.trim().toUpperCase() === currentValue.trim().toUpperCase();
  }

  async saveSetting(setting: Setting): Promise<void> {
    const key = setting.key;
    const normalizedDraft = this.normalizeSelectValue(setting, this.draftValues[key] ?? '');
    this.draftValues[key] = normalizedDraft;
    const newValue = normalizedDraft.trim();
    if (newValue === setting.value) {
      return;
    }

    const validationError = this.getValidationErrorForValue(setting, newValue);
    if (validationError) {
      this.showMessage(`"${this.descriptorOf(setting).label}": ${validationError}`, false);
      return;
    }

    this.savingKeys.add(key);
    try {
      const updated = await firstValueFrom(this.api.updateSetting(key, String(newValue)));
      setting.value = updated.value;
      this.draftValues[key] = updated.value;
      this.showMessage(`Saved "${this.descriptorOf(setting).label}".`, true);
    } catch {
      this.draftValues[key] = setting.value;
      this.showMessage(`Failed to save "${this.descriptorOf(setting).label}".`, false);
    } finally {
      this.savingKeys.delete(key);
    }
  }

  async saveSection(section: SettingSection): Promise<void> {
    const sectionSettings = this.getSectionSettings(section).filter((s) => this.isDirty(s));
    if (!sectionSettings.length) {
      this.showMessage('No pending changes in this section.', true);
      return;
    }

    const invalidSetting = sectionSettings.find((s) => this.isInvalid(s));
    if (invalidSetting) {
      this.showMessage(`Fix "${this.descriptorOf(invalidSetting).label}" before saving this section.`, false);
      return;
    }

    for (const setting of sectionSettings) {
      await this.saveSetting(setting);
    }
  }

  hasInvalidSettingsInSection(section: SettingSection): boolean {
    return this.getSectionSettings(section).some((setting) => this.isInvalid(setting));
  }

  getPlaceholder(setting: Setting): string {
    const descriptor = this.descriptorOf(setting);
    if (descriptor.placeholder) {
      return descriptor.placeholder;
    }
    if (descriptor.input === 'time') {
      return 'HH:mm';
    }
    if (descriptor.input === 'date') {
      return 'YYYY-MM-DD';
    }
    if (descriptor.input === 'number') {
      if (descriptor.min != null && descriptor.max != null) {
        return `${descriptor.min} to ${descriptor.max}`;
      }
      return 'Enter a number';
    }
    return '';
  }

  async applySolverPreset(preset: 'safe' | 'balanced' | 'fast'): Promise<void> {
    const presets: Record<'safe' | 'balanced' | 'fast', Record<string, string>> = {
      safe: {
        solver_minutes_spent_limit: '45',
        solver_unimproved_seconds_spent_limit: '90',
        solver_forager_accepted_count_limit: '8',
        solver_environment_mode: 'NON_REPRODUCIBLE',
        solver_ruin_recreate_enabled: 'true',
        solver_ruin_recreate_cluster_size: '10'
      },
      balanced: {
        solver_minutes_spent_limit: '30',
        solver_unimproved_seconds_spent_limit: '45',
        solver_forager_accepted_count_limit: '4',
        solver_environment_mode: 'NON_REPRODUCIBLE'
      },
      fast: {
        solver_minutes_spent_limit: '20',
        solver_unimproved_seconds_spent_limit: '30',
        solver_forager_accepted_count_limit: '2',
        solver_environment_mode: 'NON_REPRODUCIBLE'
      }
    };

    const overrides = presets[preset];
    Object.entries(overrides).forEach(([key, value]) => {
      if (this.draftValues[key] !== undefined) {
        this.draftValues[key] = value;
      }
    });
    this.showMessage(`Applied ${preset} preset. Click "Save Section" under Solver Speed And Accuracy.`, true);
  }

  async regenerateTimeslots(): Promise<void> {
    this.regenerating = true;
    this.regenerateMessage = '';
    try {
      const res = await firstValueFrom(this.api.regenerateTimeslots());
      this.regenerateSuccess = true;
      this.regenerateMessage = `✓ ${res.message}`;
    } catch {
      this.regenerateSuccess = false;
      this.regenerateMessage = '✗ Failed to regenerate timeslots.';
    } finally {
      this.regenerating = false;
    }
  }

  async toggleSystemEnabled(): Promise<void> {
    const newValue = !this.unavailabilitySettings.systemEnabled;
    try {
      const data = await firstValueFrom(this.api.updateUnavailabilitySystemSettings({ systemEnabled: newValue }));
      this.unavailabilitySettings = { systemEnabled: data.systemEnabled, requestsOpen: data.requestsOpen };
      this.showUnavailabilityMessage(`Unavailability system ${newValue ? 'enabled' : 'disabled'}.`, true);
    } catch {
      this.showUnavailabilityMessage('Failed to update system status.', false);
    }
  }

  async toggleRequestsOpen(): Promise<void> {
    const newValue = !this.unavailabilitySettings.requestsOpen;
    try {
      const data = await firstValueFrom(this.api.updateUnavailabilitySystemSettings({ requestsOpen: newValue }));
      this.unavailabilitySettings = { systemEnabled: data.systemEnabled, requestsOpen: data.requestsOpen };
      this.showUnavailabilityMessage(`Requests ${newValue ? 'opened' : 'closed'}.`, true);
    } catch {
      this.showUnavailabilityMessage('Failed to update request window.', false);
    }
  }

  private showUnavailabilityMessage(message: string, success: boolean): void {
    this.unavailabilityMessage = message;
    this.unavailabilitySuccess = success;
    setTimeout(() => (this.unavailabilityMessage = ''), 3000);
  }

  showMessage(message: string, success: boolean): void {
    this.globalMessage = message;
    this.globalSuccess = success;
    setTimeout(() => (this.globalMessage = ''), 3500);
  }

  cancelWipe(): void {
    this.showWipeConfirmation = false;
    this.wipeConfirmText = '';
    this.wipeMessage = '';
  }

  async executeSystemWipe(): Promise<void> {
    if (this.wipeConfirmText !== 'DELETE') return;
    this.wiping = true;
    this.wipeMessage = '';
    try {
      const res = await firstValueFrom(this.api.wipeSystemData());
      this.wipeSuccess = true;
      this.wipeMessage = `✓ ${res.message} (${res.totalDeleted} records deleted).`;
      setTimeout(() => this.cancelWipe(), 1800);
    } catch (err: any) {
      this.wipeSuccess = false;
      this.wipeMessage = `✗ Failed to wipe data: ${err?.error?.message || 'Unknown error'}`;
    } finally {
      this.wiping = false;
    }
  }
}
