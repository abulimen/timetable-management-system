import { AfterViewInit, Component, ElementRef, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import Handsontable from 'handsontable';
import 'handsontable/dist/handsontable.full.css';
import { EntityType, getColumnDefinitions, getCSVHeaders, ValidatedColDef } from './column-definitions';
import { ReferenceDataService } from '../services/reference-data.service';

/**
 * Custom editor for multi-value fields (pipe-separated).
 * Shows autocomplete suggestions as user types after each "|".
 */
class MultiValueEditor extends Handsontable.editors.TextEditor {
  private suggestionsList: HTMLDivElement | null = null;
  private availableValues: string[] = [];
  private currentSuggestions: string[] = [];
  private highlightedIndex = -1; // Track currently highlighted suggestion

  override prepare(row: number, col: number, prop: string | number, td: HTMLTableCellElement, originalValue: any, cellProperties: any): void {
    super.prepare(row, col, prop, td, originalValue, cellProperties);
    this.availableValues = cellProperties.source || [];
  }

  override open(): void {
    super.open();
    this.createSuggestionsDropdown();
    this.attachInputListeners();
  }

  override close(): void {
    this.removeSuggestionsDropdown();
    super.close();
  }

  private createSuggestionsDropdown(): void {
    if (this.suggestionsList) return;

    this.suggestionsList = document.createElement('div');
    this.suggestionsList.className = 'multi-value-suggestions';
    this.suggestionsList.style.cssText = `
      position: absolute;
      background: white;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
      max-height: 200px;
      overflow-y: auto;
      z-index: 10000;
      display: none;
      min-width: 200px;
    `;
    document.body.appendChild(this.suggestionsList);
  }

  private removeSuggestionsDropdown(): void {
    if (this.suggestionsList) {
      this.suggestionsList.remove();
      this.suggestionsList = null;
    }
  }

  private attachInputListeners(): void {
    const input = this.TEXTAREA as HTMLTextAreaElement;
    if (!input) return;

    input.addEventListener('input', () => this.updateSuggestions());
    input.addEventListener('keydown', (e) => this.handleKeydown(e));
  }

  private updateSuggestions(): void {
    const input = this.TEXTAREA as HTMLTextAreaElement;
    if (!input || !this.suggestionsList) return;

    const fullValue = input.value;
    const cursorPos = input.selectionStart || 0;

    // Find the current segment being typed (after last |)
    const beforeCursor = fullValue.substring(0, cursorPos);
    const lastPipeIndex = beforeCursor.lastIndexOf('|');
    const currentSegment = beforeCursor.substring(lastPipeIndex + 1).trim();

    // Get already entered values to exclude from suggestions
    const enteredValues = fullValue.split('|').map(v => v.trim()).filter(v => v);

    // Filter suggestions based on current segment
    if (currentSegment.length > 0) {
      this.currentSuggestions = this.availableValues.filter(val =>
        val.toLowerCase().includes(currentSegment.toLowerCase()) &&
        !enteredValues.includes(val)
      );
    } else {
      this.currentSuggestions = this.availableValues.filter(val => !enteredValues.includes(val));
    }

    this.renderSuggestions(currentSegment);
  }

  private renderSuggestions(currentSegment: string): void {
    if (!this.suggestionsList) return;

    if (this.currentSuggestions.length === 0) {
      this.suggestionsList.style.display = 'none';
      return;
    }

    this.suggestionsList.innerHTML = '';
    this.highlightedIndex = -1; // Reset highlighted index

    this.currentSuggestions.slice(0, 10).forEach((suggestion, index) => {
      const item = document.createElement('div');
      item.className = 'suggestion-item';
      item.textContent = suggestion;
      item.setAttribute('data-index', index.toString());
      item.style.cssText = `
        padding: 8px 12px;
        cursor: pointer;
        font-size: 13px;
        transition: background 0.1s;
      `;

      item.addEventListener('mouseenter', () => {
        this.highlightSuggestion(index);
      });
      item.addEventListener('mouseleave', () => {
        this.unhighlightSuggestion();
      });
      item.addEventListener('click', () => this.selectSuggestion(suggestion));

      this.suggestionsList!.appendChild(item);
    });

    // Position dropdown
    const input = this.TEXTAREA as HTMLTextAreaElement;
    if (input) {
      const rect = input.getBoundingClientRect();
      this.suggestionsList.style.left = `${rect.left}px`;
      this.suggestionsList.style.top = `${rect.bottom + 4}px`;
      this.suggestionsList.style.width = `${rect.width}px`;
      this.suggestionsList.style.display = 'block';
    }
  }

  private selectSuggestion(suggestion: string): void {
    const input = this.TEXTAREA as HTMLTextAreaElement;
    if (!input) return;

    const fullValue = input.value;
    const cursorPos = input.selectionStart || 0;

    // Replace current segment with the suggestion
    const beforeCursor = fullValue.substring(0, cursorPos);
    const afterCursor = fullValue.substring(cursorPos);
    const lastPipeIndex = beforeCursor.lastIndexOf('|');

    let newValue: string;
    if (lastPipeIndex === -1) {
      // First value
      newValue = suggestion + afterCursor;
    } else {
      // Add after pipe
      newValue = fullValue.substring(0, lastPipeIndex + 1) + ' ' + suggestion + afterCursor;
    }

    input.value = newValue;
    input.setSelectionRange(newValue.length - afterCursor.length, newValue.length - afterCursor.length);

    if (this.suggestionsList) {
      this.suggestionsList.style.display = 'none';
    }
    this.highlightedIndex = -1;
    input.focus();
  }

  private handleKeydown(e: KeyboardEvent): void {
    // Handle keyboard navigation in suggestions dropdown
    if (this.suggestionsList && this.suggestionsList.style.display !== 'none') {
      const visibleSuggestions = this.currentSuggestions.slice(0, 10);

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        e.stopPropagation();
        this.highlightedIndex = Math.min(this.highlightedIndex + 1, visibleSuggestions.length - 1);
        this.highlightSuggestion(this.highlightedIndex);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        e.stopPropagation();
        this.highlightedIndex = Math.max(this.highlightedIndex - 1, 0);
        this.highlightSuggestion(this.highlightedIndex);
      } else if (e.key === 'Enter' && this.highlightedIndex >= 0) {
        e.preventDefault();
        e.stopPropagation();
        this.selectSuggestion(visibleSuggestions[this.highlightedIndex]);
      } else if (e.key === 'Tab' && this.highlightedIndex >= 0) {
        e.preventDefault();
        e.stopPropagation();
        this.selectSuggestion(visibleSuggestions[this.highlightedIndex]);
      } else if (e.key === 'Escape') {
        e.stopPropagation();
        this.suggestionsList.style.display = 'none';
        this.highlightedIndex = -1;
      }
    }
  }

  private highlightSuggestion(index: number): void {
    if (!this.suggestionsList) return;

    const items = this.suggestionsList.querySelectorAll('.suggestion-item');
    items.forEach((item, i) => {
      if (i === index) {
        (item as HTMLElement).style.background = '#dbeafe';
        (item as HTMLElement).style.color = '#1e40af';
        this.highlightedIndex = index;
      } else {
        (item as HTMLElement).style.background = 'white';
        (item as HTMLElement).style.color = '';
      }
    });
  }

  private unhighlightSuggestion(): void {
    if (!this.suggestionsList) return;

    const items = this.suggestionsList.querySelectorAll('.suggestion-item');
    items.forEach(item => {
      (item as HTMLElement).style.background = 'white';
      (item as HTMLElement).style.color = '';
    });
    this.highlightedIndex = -1;
  }
}

/**
 * Pill renderer for multi-value cells.
 * Displays pipe-separated values as colored pills.
 */
function multiValuePillRenderer(
  instance: any,
  td: HTMLTableCellElement,
  row: number,
  col: number,
  prop: string | number,
  value: any,
  cellProperties: any
): void {
  // Clear existing content
  td.innerHTML = '';
  td.style.padding = '4px';
  td.style.whiteSpace = 'normal';
  td.style.lineHeight = '1.5';

  if (!value || String(value).trim() === '') {
    td.style.background = '';
    return;
  }

  // Split by pipe and create pills
  const values = String(value).split('|').map(v => v.trim()).filter(v => v);

  if (values.length === 0) {
    td.style.background = '';
    return;
  }

  // Create container for pills
  const container = document.createElement('div');
  container.style.cssText = `
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
  `;

  values.forEach(val => {
    const pill = document.createElement('span');
    pill.className = 'value-pill';
    pill.textContent = val;
    pill.style.cssText = `
      display: inline-block;
      padding: 2px 8px;
      background: #e0e7ff;
      color: #3730a3;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
    `;
    container.appendChild(pill);
  });

  td.appendChild(container);
}

@Component({
  selector: 'app-inline-editor',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Fullscreen Modal Overlay -->
    <div class="fullscreen-modal-overlay">
      <div class="fullscreen-modal">
        <!-- Modal Header -->
        <div class="modal-header">
          <div class="header-left">
            <h2 class="modal-title">
              <span class="entity-badge">{{ entityType | uppercase }}</span>
              <span class="filename" *ngIf="fileName">{{ fileName }}</span>
              <span class="filename" *ngIf="!fileName">New Data</span>
            </h2>
          </div>
          <div class="header-right">
            <button class="btn btn-sm btn-ghost" (click)="requestClose()">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
              Close
            </button>
          </div>
        </div>

        <!-- Action Toolbar -->
        <div class="action-toolbar">
          <div class="toolbar-left">
            <button class="btn btn-sm btn-primary" (click)="addRow()">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              Add Row
            </button>
            <button class="btn btn-sm btn-ghost" (click)="clearAll()">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18"></path><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path></svg>
              Clear All
            </button>
            <span class="separator">|</span>
            <button class="btn btn-sm btn-ghost" (click)="downloadCSV()">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
              Download CSV
            </button>
            <span class="separator">|</span>
            <button class="btn btn-sm btn-ghost" [class.active]="sortedByErrors" (click)="sortByErrors()">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h12M3 18h6"></path></svg>
              {{ sortedByErrors ? '✓ Errors First' : 'Sort by Errors' }}
            </button>
          </div>
          <div class="toolbar-right">
            <span class="row-count">{{ rowCount }} rows</span>
            <span *ngIf="referenceMismatchCount > 0" class="validation-summary has-errors">
              <span class="error-count">🔴 {{ referenceMismatchCount }} mismatches</span>
            </span>
            <span *ngIf="validationSummary" class="validation-summary" 
                  [class.has-errors]="validationSummary.errorCount > 0">
              <span *ngIf="validationSummary.errorCount > 0" class="error-count">
                ❌ {{ validationSummary.errorCount }} errors
              </span>
              <span *ngIf="validationSummary.errorCount === 0" class="valid-count">
                ✅ All valid
              </span>
            </span>
          </div>
        </div>

        <!-- Loading State -->
        <div *ngIf="loading" class="loading-overlay">
          <div class="spinner"></div>
          <span>Loading reference data...</span>
        </div>

        <!-- Handsontable Container (Maximized) -->
        <div #hotContainer class="hot-container" [style.display]="loading ? 'none' : 'block'"></div>

        <!-- Footer -->
        <div class="modal-footer">
          <div class="footer-left">
            <span class="tip">💡 <strong>Tips:</strong> Click row/column headers to select • Right-click for more options • Paste from Excel (Ctrl+V)</span>
          </div>
          <div class="footer-right">
            <button class="btn btn-ghost" (click)="requestClose()">Cancel</button>
            <button class="btn btn-primary" (click)="saveAndClose()">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>
              Save and Close
            </button>
          </div>
        </div>
      </div>

      <!-- Shared Tooltip Overlay -->
      <div class="error-tooltip-overlay" [class.visible]="activeTooltip" [style.top.px]="tooltipPosition.y" [style.left.px]="tooltipPosition.x" style="white-space: pre-line;">
        {{ activeTooltip }}
      </div>
    </div>
  `,
  styles: [`
    /* Fullscreen Modal Overlay */
    .fullscreen-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.6);
      z-index: 1000;
      display: flex;
      align-items: stretch;
      justify-content: stretch;
      padding: 0;
    }

    .fullscreen-modal {
      background: white;
      border-radius: 0;
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    /* Modal Header */
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 24px;
      border-bottom: 1px solid #e5e7eb;
      background: linear-gradient(to right, #f9fafb, #f3f4f6);
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .modal-title {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 0;
      font-size: 1.25rem;
      font-weight: 600;
      color: #1f2937;
    }

    .entity-badge {
      background: #3b82f6;
      color: white;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.5px;
    }

    .filename {
      color: #6b7280;
      font-weight: 400;
    }

    .header-right button {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    /* Action Toolbar */
    .action-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 24px;
      background: #f9fafb;
      border-bottom: 1px solid #e5e7eb;
    }

    .toolbar-left, .toolbar-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .toolbar-left button {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .separator {
      color: #d1d5db;
      margin: 0 4px;
    }

    .row-count {
      font-size: 0.875rem;
      color: #6b7280;
      padding: 4px 8px;
      background: #e5e7eb;
      border-radius: 4px;
    }

    .validation-summary {
      font-size: 0.875rem;
      padding: 4px 10px;
      border-radius: 4px;
    }

    .validation-summary.has-errors {
      background: rgba(239, 68, 68, 0.1);
      color: #dc2626;
    }

    .valid-count {
      color: #16a34a;
    }

    .error-count {
      color: #dc2626;
    }

    /* Loading Overlay */
    .loading-overlay {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1;
      gap: 1rem;
      color: #6b7280;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid #e5e7eb;
      border-top-color: #3b82f6;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Handsontable Container - Maximized */
    .hot-container {
      flex: 1;
      overflow: hidden;
      margin: 0;
      border-radius: 0;
    }

    :host ::ng-deep .hot-container .cell-match {
      background: rgba(34, 197, 94, 0.18) !important;
    }

    :host ::ng-deep .hot-container .cell-mismatch {
      background: rgba(239, 68, 68, 0.18) !important;
    }

    /* Modal Footer */
    .modal-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 24px;
      border-top: 1px solid #e5e7eb;
      background: #f9fafb;
    }

    .footer-left {
      font-size: 0.8rem;
      color: #9ca3af;
    }

    .footer-right {
      display: flex;
      gap: 12px;
    }

    .footer-right button {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    /* Error Visualization Styles */
    :host ::ng-deep .hot-container .cell-invalid-custom {
      background: rgba(239, 68, 68, 0.1) !important;
      border-bottom: 2px solid #ef4444 !important;
    }

    /* Valid Cell Styles */
    :host ::ng-deep .hot-container .cell-valid-custom {
      background: rgba(34, 197, 94, 0.1) !important;
    }

    :host ::ng-deep .error-icon-container {
      position: absolute;
      top: 4px;
      right: 4px;
      cursor: pointer;
      z-index: 100;
      width: 16px;
      height: 16px;
      color: #ef4444;
      background: white;
      border-radius: 50%;
      box-shadow: 0 1px 2px rgba(0,0,0,0.1);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    
    :host ::ng-deep .error-icon-container svg {
      width: 14px;
      height: 14px;
      fill: currentColor;
    }

    .error-tooltip-overlay {
      display: none;
      position: fixed; /* Fixed to viewport to avoid clipping */
      background: linear-gradient(135deg, #1f2937 0%, #111827 100%);
      color: white;
      padding: 10px 14px;
      border-radius: 8px;
      font-size: 13px;
      max-width: 360px;
      z-index: 9999; /* Very high z-index */
      box-shadow: 0 10px 25px -3px rgba(0, 0, 0, 0.3), 0 4px 6px -2px rgba(0, 0, 0, 0.1);
      border: 1px solid #374151;
      pointer-events: none;
      line-height: 1.5;
      white-space: pre-line;
    }

    .error-tooltip-overlay.visible {
      display: block;
    }

     /* Header Error Badge */
    :host ::ng-deep .header-content {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
    }
    
    :host ::ng-deep .header-error-badge {
      color: #dc2626;
      font-weight: bold;
      font-size: 0.8em;
    }

    /* Multi-value Pills */
    :host ::ng-deep .value-pill {
      display: inline-block;
      padding: 2px 8px;
      background: #e0e7ff;
      color: #3730a3;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
      margin: 2px;
    }

    /* Multi-value cells should have more height */
    :host ::ng-deep td:has(.value-pill) {
      min-height: 32px;
      vertical-align: middle;
    }

    /* Suggestion dropdown styles */
    .multi-value-suggestions .suggestion-item {
      padding: 8px 12px;
      cursor: pointer;
      font-size: 13px;
      transition: background 0.1s;
    }
  `]
})
export class InlineEditorComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('hotContainer', { static: false }) hotContainer!: ElementRef;

  @Input() entityType!: EntityType;
  @Input() gridHeight = '350px';
  @Input() initialData: Record<string, string>[] = [];
  @Input() csvFile: File | null = null; // CSV file to load into editor

  @Output() dataChange = new EventEmitter<Record<string, string>[]>();
  @Output() validationChange = new EventEmitter<{ valid: boolean; errorCount: number }>();
  @Output() closeModal = new EventEmitter<void>();
  @Output() saveData = new EventEmitter<Record<string, string>[]>();

  // Computed filename from csvFile
  get fileName(): string | null {
    return this.csvFile?.name || null;
  }

  private refDataService = inject(ReferenceDataService);
  private hotInstance: Handsontable | null = null;

  loading = true;
  rowCount = 0;
  validationSummary: { valid: boolean; errorCount: number } | null = null;
  referenceMismatchCount = 0;

  // Tooltip state
  activeTooltip: string | null = null;
  tooltipPosition = { x: 0, y: 0 };

  private pendingCsvContent: string | null = null;
  private hasLoadedInitialData = false; // Track if we've loaded initialData (prevent file overwriting)

  private columnDefs: ValidatedColDef[] = [];
  private headers: string[] = [];

  private cellStatus = new Map<string, 'match' | 'mismatch' | null>();
  private cellErrors = new Map<string, string>(); // Store specific error messages
  private columnErrorCounts = new Map<number, number>(); // Store error counts per column

  // Sort state
  sortedByErrors = false;
  private originalData: any[][] | null = null; // Store original order for toggle

  async ngOnInit() {
    // Load reference data
    try {
      await this.refDataService.loadAll();
    } catch (e) {
      console.error('Failed to load reference data:', e);
    }

    // Get column definitions
    this.columnDefs = getColumnDefinitions(this.entityType, this.refDataService);
    this.headers = getCSVHeaders(this.entityType);

    if (this.pendingCsvContent && this.headers.length > 0) {
      const content = this.pendingCsvContent;
      this.pendingCsvContent = null;
      this.loadFromCSVString(content);
    }

    this.loading = false;
  }

  async ngAfterViewInit() {
    // Load data based on what we have:
    // 1. If initialData has content (edited data), use it - don't load from file
    // 2. If no initialData AND we have a file, load from file
    const hasEditedData = this.initialData && this.initialData.length > 0;

    if (hasEditedData) {
      // We have edited data - use it, don't load from file
      this.hasLoadedInitialData = true;
    } else if (this.csvFile && this.hasLoadedInitialData === false) {
      // No edited data AND we haven't loaded yet - load from CSV file
      await this.loadFromFile(this.csvFile);
      this.hasLoadedInitialData = true;
    }

    // Wait for loading to complete, then initialize
    setTimeout(() => this.initHandsontable(), 100);
  }

  async ngOnChanges(changes: SimpleChanges) {
    if (!changes['csvFile']) return;

    const current: File | null = changes['csvFile'].currentValue ?? null;
    // Only load from file if:
    // 1. File is provided
    // 2. We haven't already loaded initial data (to preserve edits)
    // 3. We don't have edited initialData
    if (current && !this.hasLoadedInitialData && (!this.initialData || this.initialData.length === 0)) {
      try {
        await this.loadFromFile(current);
        this.hasLoadedInitialData = true;
      } catch (e) {
        console.error('Failed to load CSV file into inline editor:', e);
      }
    }
  }

  ngOnDestroy() {
    if (this.hotInstance) {
      this.hotInstance.destroy();
      this.hotInstance = null;
    }
  }

  private initHandsontable() {
    if (this.loading || !this.hotContainer?.nativeElement) {
      setTimeout(() => this.initHandsontable(), 100);
      return;
    }

    const container = this.hotContainer.nativeElement;

    // Prepare initial data
    let data: any[][] = [];
    if (this.initialData.length > 0) {
      data = this.initialData.map(row =>
        this.headers.map(h => row[h] || '')
      );
    } else {
      // Start with 5 empty rows
      data = Array(5).fill(null).map(() =>
        this.headers.map(() => '')
      );
    }

    // Build column configs for Handsontable
    const columns = this.columnDefs.map(col => {
      const config: any = {
        data: col.field,
        width: 150
      };

      // Set up autocomplete for columns with autocompleteValues
      if (col.autocompleteValues) {
        config.type = 'autocomplete';
        config.source = col.autocompleteValues();
        config.strict = false;
        config.allowInvalid = true;
      }

      return config;
    });

    // Create Handsontable instance
    this.hotInstance = new Handsontable(container, {
      data: data,
      colHeaders: (colIndex: number) => {
        const colDef = this.columnDefs[colIndex];
        const header = colDef?.headerName || this.headers[colIndex] || '';
        const count = this.columnErrorCounts.get(colIndex) || 0;
        const supportsMultiple = colDef?.supportsMultiple || false;

        let headerHtml = header;
        if (supportsMultiple) {
          headerHtml = `${header} <span style="color: #6b7280; font-size: 0.75em; font-weight: normal;">(use | for multiple)</span>`;
        }

        if (count > 0) {
          return `<div class="header-content">${headerHtml} <span class="header-error-badge">(${count})</span></div>`;
        }
        return `<div class="header-content">${headerHtml}</div>`;
      },
      columns: this.headers.map((field, i) => {
        const colDef = this.columnDefs[i];
        const config: any = { data: i };

        if (colDef?.placeholder) {
          config.placeholder = colDef.placeholder;
        }

        if (colDef?.autocompleteValues) {
          const supportsMultiple = colDef.supportsMultiple || false;

          if (supportsMultiple) {
            // Use custom multi-value editor
            config.editor = MultiValueEditor;
            config.source = colDef.autocompleteValues();
            // Use pill renderer wrapped with error renderer
            config.renderer = (instance: any, td: HTMLTableCellElement, row: number, col: number, prop: string | number, value: any, cellProperties: any) => {
              // First render pills
              multiValuePillRenderer(instance, td, row, col, prop, value, cellProperties);

              // Then add error indicators if needed
              const key = `${row}:${col}`;
              const errorMsg = this.cellErrors.get(key);

              if (errorMsg) {
                td.classList.add('cell-invalid-custom');

                const errorContainer = document.createElement('div');
                errorContainer.className = 'error-icon-container';
                errorContainer.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
                </svg>`;

                errorContainer.addEventListener('mouseenter', () => {
                  const rect = errorContainer.getBoundingClientRect();
                  this.activeTooltip = errorMsg;
                  const tooltipWidth = 360;
                  const tooltipHeight = 80;
                  const padding = 10;
                  let x = rect.right + padding;
                  if (x + tooltipWidth > window.innerWidth) x = Math.max(padding, rect.left - tooltipWidth - padding);
                  let y = rect.top - padding;
                  if (y + tooltipHeight > window.innerHeight) y = Math.max(padding, rect.top - tooltipHeight - padding);
                  this.tooltipPosition = { x, y };
                });

                errorContainer.addEventListener('mouseleave', () => {
                  this.activeTooltip = null;
                });

                td.style.position = 'relative';
                td.appendChild(errorContainer);
              } else {
                td.classList.remove('cell-invalid-custom');
                // Add green styling for valid cells that have content
                if (value && String(value).trim() !== '') {
                  td.classList.add('cell-valid-custom');
                } else {
                  td.classList.remove('cell-valid-custom');
                }
              }
            };
          } else {
            config.type = 'autocomplete';
            config.source = colDef.autocompleteValues();
            config.strict = false;
            config.renderer = this.getErrorRenderer('autocomplete');
          }
        } else {
          // Use text renderer with error handling
          config.renderer = this.getErrorRenderer('text');
        }

        return config;
      }),
      cells: (row, col) => {
        const cellProperties: any = {};
        const key = `${row}:${col}`;
        const status = this.cellStatus.get(key);

        if (status === 'match') {
          cellProperties.className = 'cell-match';
        } else if (status === 'mismatch') {
          cellProperties.className = 'cell-mismatch';
        }
        // Note: cell-invalid class is added by the renderer now based on cellErrors map

        return cellProperties;
      },
      rowHeaders: true,
      height: '100%',
      stretchH: 'all',
      autoWrapRow: true,
      autoWrapCol: true,
      manualColumnResize: true,
      manualRowResize: true,
      contextMenu: true,
      minSpareRows: 1,
      licenseKey: 'non-commercial-and-evaluation',
      afterChange: (changes, source) => {
        if (source !== 'loadData') {
          this.onDataChange();
        }
      },
      afterCreateRow: () => this.onDataChange(),
      afterRemoveRow: () => this.onDataChange(),
      // Allow HTML in headers
      afterGetColHeader: (col, TH) => {
        // Handsontable renders HTML strings in colHeaders automatically if passed as string?
        // Actually for security sometimes it escapes. But function return value is usually innerHTML.
        // Let's verify defaults.
      }
    });

    this.updateRowCount();
    this.validateAll();
  }

  // Helper factory for renderers
  private getErrorRenderer(type: string) {
    return (instance: any, td: HTMLTableCellElement, row: number, col: number, prop: string | number, value: any, cellProperties: any) => {
      // Base renderer
      let baseRenderer = Handsontable.renderers.TextRenderer;
      if (type === 'autocomplete') {
        baseRenderer = Handsontable.renderers.AutocompleteRenderer;
      }

      baseRenderer.apply(this, [instance, td, row, col, prop, value, cellProperties]);

      // Error handling
      const key = `${row}:${col}`;
      const errorMsg = this.cellErrors.get(key);

      if (errorMsg) {
        td.classList.add('cell-invalid-custom'); // Custom class for red background

        // Create container if not exists (though base renderer usually wipes content)
        // With text renderer it wipes. So we append.

        const errorContainer = document.createElement('div');
        errorContainer.className = 'error-icon-container';
        // SVG Info Icon
        errorContainer.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
        </svg>`;

        // Tooltip logic using shared state
        errorContainer.addEventListener('mouseenter', (e) => {
          const rect = errorContainer.getBoundingClientRect();
          this.activeTooltip = errorMsg;

          // Calculate tooltip dimensions (estimate based on content)
          const tooltipWidth = 320; // max-width from CSS
          const tooltipHeight = 80; // estimate
          const padding = 10;

          // Determine horizontal position
          let x = rect.right + padding;
          if (x + tooltipWidth > window.innerWidth) {
            // Position to the left if not enough space on right
            x = rect.left - tooltipWidth - padding;
            if (x < 0) x = padding; // Fallback
          }

          // Determine vertical position
          let y = rect.top - padding;
          if (y + tooltipHeight > window.innerHeight) {
            // Position above if too close to bottom
            y = rect.top - tooltipHeight - padding;
            if (y < 0) y = padding; // Fallback
          }
          if (y < 0) y = padding;

          this.tooltipPosition = { x, y };
        });

        errorContainer.addEventListener('mouseleave', () => {
          this.activeTooltip = null;
        });

        // Toggle on click
        errorContainer.addEventListener('click', (e) => {
          e.stopPropagation();
        });

        td.style.position = 'relative'; // Ensure positioning context
        td.appendChild(errorContainer);
      } else {
        td.classList.remove('cell-invalid-custom');
        // Add green styling for valid cells that have content
        if (value && String(value).trim() !== '') {
          td.classList.add('cell-valid-custom');
        } else {
          td.classList.remove('cell-valid-custom');
        }
      }
    };
  }

  addRow() {
    if (this.hotInstance) {
      const rowCount = this.hotInstance.countRows();
      this.hotInstance.alter('insert_row_below', rowCount - 1);
      this.updateRowCount();
    }
  }

  removeSelectedRows() {
    if (this.hotInstance) {
      const selected = this.hotInstance.getSelected();
      if (selected && selected.length > 0) {
        // Get unique row indices
        const rows = [...new Set(selected.map(s => s[0]))].sort((a, b) => b - a);
        rows.forEach(row => {
          if (row >= 0) {
            this.hotInstance!.alter('remove_row', row);
          }
        });
        this.updateRowCount();
        this.onDataChange();
      }
    }
  }

  clearAll() {
    if (this.hotInstance) {
      const emptyData = Array(5).fill(null).map(() =>
        this.headers.map(() => '')
      );
      this.hotInstance.loadData(emptyData);
      this.updateRowCount();
      this.onDataChange();
    }
  }

  private updateRowCount() {
    if (this.hotInstance) {
      // Count non-empty rows
      const data = this.hotInstance.getData();
      this.rowCount = data.filter((row: any[]) =>
        row.some((cell: any) => cell && String(cell).trim())
      ).length;
    }
  }

  private onDataChange() {
    this.updateRowCount();
    this.validateAll();
    this.dataChange.emit(this.getData());
  }

  private validateAll() {
    if (!this.hotInstance) return;

    const data = this.hotInstance.getData();
    let errorCount = 0;
    let referenceMismatchCount = 0;
    this.cellStatus.clear();
    this.cellErrors.clear();
    this.columnErrorCounts.clear();

    // Initialize col counts
    this.headers.forEach((_, i) => this.columnErrorCounts.set(i, 0));

    // Build all rows as objects for cross-row validation
    const allRowObjects = data.map(row => {
      const obj: Record<string, string> = {};
      this.headers.forEach((h, i) => obj[h] = String(row[i] || ''));
      return obj;
    });

    data.forEach((row, rowIndex) => {
      // Skip empty rows
      if (!row.some((cell: any) => cell && String(cell).trim())) return;

      // Build rowData object for this row
      const rowData: Record<string, string> = {};
      this.headers.forEach((h, i) => rowData[h] = String(row[i] || ''));

      this.headers.forEach((header, colIndex) => {
        const colDef = this.columnDefs[colIndex];
        if (colDef?.validator) {
          const value = row[colIndex];
          // Pass value, rowData, and allRowObjects
          const result = colDef.validator(String(value || ''), rowData, allRowObjects);

          if (!result.valid) {
            errorCount++;
            const key = `${rowIndex}:${colIndex}`;
            this.cellErrors.set(key, result.message || 'Invalid value');

            // Increment column error count
            const currentCount = this.columnErrorCounts.get(colIndex) || 0;
            this.columnErrorCounts.set(colIndex, currentCount + 1);
          }

          if (colDef.referenceCheck) {
            // We can keep the cellStatus logic for match/mismatch too
            // But if it's an error (red), it usually overrides mismatch (yellow/red).
            if (String(value || '').trim()) { // Only check if cell has content
              const key = `${rowIndex}:${colIndex}`;
              if (result.valid) {
                this.cellStatus.set(key, 'match');
              } else {
                this.cellStatus.set(key, 'mismatch');
                referenceMismatchCount++;
              }
            }
          }
        }
      });
    });

    this.validationSummary = {
      valid: errorCount === 0,
      errorCount
    };

    this.referenceMismatchCount = referenceMismatchCount;

    this.hotInstance.render();

    this.validationChange.emit(this.validationSummary);
  }

  /**
   * Get data ready for CSV export/import.
   */
  getData(): Record<string, string>[] {
    if (!this.hotInstance) return [];

    const data = this.hotInstance.getData();
    const result: Record<string, string>[] = [];

    data.forEach(row => {
      // Skip empty rows
      if (!row.some((cell: any) => cell && String(cell).trim())) return;

      const rowObj: Record<string, string> = {};
      this.headers.forEach((header, i) => {
        rowObj[header] = String(row[i] || '');
      });
      result.push(rowObj);
    });

    return result;
  }

  /**
   * Request to close the modal (emits closeModal event).
   */
  requestClose() {
    this.closeModal.emit();
  }

  /**
   * Save data and close the modal.
   */
  saveAndClose() {
    const data = this.getData();
    this.saveData.emit(data);
    this.closeModal.emit();
  }

  /**
   * Download current data as CSV file.
   */
  downloadCSV() {
    const data = this.getData();
    if (data.length === 0) return;

    // Build CSV content
    const csvRows: string[] = [];
    csvRows.push(this.headers.join(','));

    data.forEach(row => {
      const values = this.headers.map(h => {
        const val = row[h] || '';
        // Escape quotes and wrap in quotes if contains comma or newline
        if (val.includes(',') || val.includes('\n') || val.includes('"')) {
          return `"${val.replace(/"/g, '""')}"`;
        }
        return val;
      });
      csvRows.push(values.join(','));
    });

    const csvContent = csvRows.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.entityType}_data.csv`;
    link.click();

    URL.revokeObjectURL(url);
  }

  /**
   * Sort rows by error status - rows with errors come first.
   * Toggles between error-first order and original order.
   */
  sortByErrors() {
    if (!this.hotInstance) return;

    const data = this.hotInstance.getData();

    if (this.sortedByErrors) {
      // Restore original order if we saved it
      if (this.originalData) {
        this.hotInstance.loadData(this.originalData);
        this.originalData = null;
      }
      this.sortedByErrors = false;
    } else {
      // Save original order
      this.originalData = data.map(row => [...row]);

      // Create array of [rowIndex, errorCount] pairs
      const rowsWithErrorCount: { row: any[]; errorCount: number }[] = data.map((row, rowIndex) => {
        let errorCount = 0;
        for (let colIndex = 0; colIndex < this.headers.length; colIndex++) {
          const key = `${rowIndex}:${colIndex}`;
          if (this.cellErrors.has(key)) {
            errorCount++;
          }
        }
        return { row: [...row], errorCount };
      });

      // Sort: rows with errors first (descending by error count), then others
      rowsWithErrorCount.sort((a, b) => b.errorCount - a.errorCount);

      // Load sorted data
      const sortedData = rowsWithErrorCount.map(item => item.row);
      this.hotInstance.loadData(sortedData);

      this.sortedByErrors = true;
    }

    // Re-validate to update error maps with new row indices
    this.validateAll();
  }

  /**
   * Load data from a CSV File object.
   */
  async loadFromFile(file: File): Promise<void> {
    const text = await file.text();
    this.loadFromCSVString(text);
  }

  /**
   * Load data from a CSV string into the grid.
   */
  loadFromCSVString(csvContent: string) {
    if (this.headers.length === 0) {
      this.pendingCsvContent = csvContent;
      return;
    }

    const lines = csvContent.split('\n');
    if (lines.length < 2) return;

    const csvHeaders = this.parseCSVLine(lines[0]);

    // Map CSV columns to expected columns
    const columnMap = new Map<number, number>();
    csvHeaders.forEach((h, csvIndex) => {
      const normalized = h.toLowerCase().trim();
      const headerIndex = this.headers.findIndex(eh => eh.toLowerCase() === normalized);
      if (headerIndex !== -1) {
        columnMap.set(csvIndex, headerIndex);
      }
    });

    const newData: any[][] = [];
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      const values = this.parseCSVLine(line);
      const row = this.headers.map(() => '');

      columnMap.forEach((headerIndex, csvIndex) => {
        if (csvIndex < values.length) {
          row[headerIndex] = values[csvIndex];
        }
      });

      newData.push(row);
    }

    // Store for later loading into grid - ONLY if we don't already have initialData
    // This prevents overwriting edited data when component is recreated
    if (!this.initialData || this.initialData.length === 0) {
      this.initialData = newData.map(row => {
        const obj: Record<string, string> = {};
        this.headers.forEach((h, i) => obj[h] = row[i] || '');
        return obj;
      });
    }

    // If the grid is already initialized, load immediately.
    if (this.hotInstance) {
      this.hotInstance.loadData(newData);
      this.updateRowCount();
      this.onDataChange();
    }
  }

  private parseCSVLine(line: string): string[] {
    const result: string[] = [];
    let current = '';
    let inQuotes = false;

    for (let i = 0; i < line.length; i++) {
      const char = line[i];
      const nextChar = line[i + 1];

      if (inQuotes) {
        if (char === '"') {
          if (nextChar === '"') {
            current += '"';
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          current += char;
        }
      } else {
        if (char === '"') {
          inQuotes = true;
        } else if (char === ',') {
          result.push(current.trim());
          current = '';
        } else {
          current += char;
        }
      }
    }

    result.push(current.trim());
    return result;
  }
}
