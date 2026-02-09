import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, StudentGroup, Room, Lecturer } from '../../core/services/api.service';

interface SpecialEvent {
    id: number;
    name: string;
    description: string;
    dayOfWeek: string;
    startTime: string;
    endTime: string;
    durationHours: number;
    roomId: number | null;
    roomName: string | null;
    lecturerId: number | null;
    lecturerName: string | null;
    online: boolean;
    active: boolean;
    studentGroupIds: number[];
    studentGroupNames: string[];
}

@Component({
    selector: 'app-special-events',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Special Events</h1>
          <p class="text-sm text-secondary-500 mt-1">Fixed events like interdisciplinary seminars that block timeslots</p>
        </div>
        <button (click)="showAddForm = true" class="btn btn-primary">Add Special Event</button>
      </div>

      <!-- Add/Edit Form -->
      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingEvent ? 'Edit' : 'Add' }} Special Event</h2>
        <form (ngSubmit)="saveEvent()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Event Name</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required
                     placeholder="e.g., 200 Level Interdisciplinary Seminar">
            </div>
            <div>
              <label class="label">Description</label>
              <input type="text" [(ngModel)]="formData.description" name="description" class="input"
                     placeholder="Optional description">
            </div>
          </div>
          
          <div class="grid grid-cols-4 gap-4">
            <div>
              <label class="label">Day of Week</label>
              <select [(ngModel)]="formData.dayOfWeek" name="dayOfWeek" class="input" required>
                <option value="MONDAY">Monday</option>
                <option value="TUESDAY">Tuesday</option>
                <option value="WEDNESDAY">Wednesday</option>
                <option value="THURSDAY">Thursday</option>
                <option value="FRIDAY">Friday</option>
              </select>
            </div>
            <div>
              <label class="label">Start Time</label>
              <input type="time" [(ngModel)]="formData.startTime" name="startTime" class="input" required>
            </div>
            <div>
              <label class="label">Duration (hours)</label>
              <input type="number" [(ngModel)]="formData.durationHours" name="durationHours" class="input" required min="1" max="4">
            </div>
            <div class="flex items-end">
              <label class="flex items-center gap-2 pb-2">
                <input type="checkbox" [(ngModel)]="formData.online" (ngModelChange)="onOnlineChanged()" name="online" class="w-4 h-4">
                <span class="text-sm">🌐 Online Event</span>
              </label>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4" *ngIf="!formData.online">
            <div>
              <label class="label">Room</label>
              <select [(ngModel)]="formData.roomId" (ngModelChange)="onRoomChanged()" name="roomId" class="input">
                <option [ngValue]="null">Select Room</option>
                <option *ngFor="let r of rooms" [ngValue]="r.id">{{ r.name }} ({{ r.capacity }})</option>
              </select>
            </div>
            <div>
              <label class="label">Lecturer (optional)</label>
              <select [(ngModel)]="formData.lecturerId" name="lecturerId" class="input">
                <option [ngValue]="null">No Lecturer</option>
                <option *ngFor="let l of lecturers" [ngValue]="l.id">{{ l.name }}</option>
              </select>
            </div>
          </div>

          <!-- Multi-Group Selection -->
          <div>
            <label class="label mb-2">Affected Student Groups (select all that must attend)</label>
            <div class="flex flex-wrap gap-2 max-h-40 overflow-y-auto p-2 border border-secondary-300 dark:border-secondary-600 rounded-lg">
              <label *ngFor="let g of parentGroups" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [class.bg-primary-100]="isGroupSelected(g.id)"
                     [class.border-primary-500]="isGroupSelected(g.id)"
                     [class.dark:bg-primary-900]="isGroupSelected(g.id)"
                     [class.border-secondary-300]="!isGroupSelected(g.id)"
                     [class.dark:border-secondary-600]="!isGroupSelected(g.id)">
                <input type="checkbox" 
                       [checked]="isGroupSelected(g.id)"
                       (change)="toggleGroup(g.id)"
                       class="mr-2">
                <span class="text-sm">{{ g.name }} ({{ g.size }})</span>
              </label>
            </div>
            <p class="text-xs text-secondary-500 mt-1">All selected groups will be blocked from having other classes during this event</p>
            <p *ngIf="!formData.online && getSelectedRoom()" class="text-xs mt-1 text-secondary-500">
              Selected population: {{ getSelectedPopulation() }} / Room capacity: {{ getSelectedRoom()!.capacity }}
            </p>
            <p *ngIf="capacityError" class="text-xs mt-1 text-red-600">{{ capacityError }}</p>
          </div>

          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary" [disabled]="isCapacityExceeded()">Save Event</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- Events List -->
      <div class="space-y-4">
        <div *ngFor="let event of events" 
             class="card p-4 border-l-4"
             [class.border-l-green-500]="event.active"
             [class.border-l-secondary-400]="!event.active"
             [class.opacity-60]="!event.active">
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-3">
                <h3 class="font-semibold text-lg">{{ event.name }}</h3>
                <span *ngIf="event.active" class="px-2 py-0.5 bg-green-100 text-green-800 text-xs rounded-full">Active</span>
                <span *ngIf="!event.active" class="px-2 py-0.5 bg-secondary-200 text-secondary-600 text-xs rounded-full">Inactive</span>
                <span *ngIf="event.online" class="px-2 py-0.5 bg-blue-100 text-blue-800 text-xs rounded-full">🌐 Online</span>
              </div>
              <p *ngIf="event.description" class="text-sm text-secondary-500 mt-1">{{ event.description }}</p>
              
              <div class="flex flex-wrap items-center gap-4 mt-3 text-sm">
                <div class="flex items-center gap-1">
                  <span class="font-medium">📅</span>
                  <span>{{ event.dayOfWeek | titlecase }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <span class="font-medium">⏰</span>
                  <span>{{ event.startTime }} - {{ event.endTime }} ({{ event.durationHours }}h)</span>
                </div>
                <div *ngIf="event.roomName" class="flex items-center gap-1">
                  <span class="font-medium">🏫</span>
                  <span>{{ event.roomName }}</span>
                </div>
                <div *ngIf="event.lecturerName" class="flex items-center gap-1">
                  <span class="font-medium">👨‍🏫</span>
                  <span>{{ event.lecturerName }}</span>
                </div>
              </div>
              
              <div class="mt-3">
                <span class="text-xs text-secondary-500 mr-2">Affected Groups:</span>
                <span *ngFor="let gn of event.studentGroupNames" 
                      class="inline-block px-2 py-0.5 bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200 text-xs rounded-full mr-1">
                  {{ gn }}
                </span>
              </div>
            </div>
            
            <div class="flex gap-2 ml-4">
              <button (click)="toggleActive(event)" 
                      class="btn btn-sm" 
                      [class.btn-secondary]="event.active"
                      [class.btn-primary]="!event.active">
                {{ event.active ? 'Deactivate' : 'Activate' }}
              </button>
              <button (click)="editEvent(event)" class="btn btn-sm btn-secondary">Edit</button>
              <button (click)="deleteEvent(event.id)" class="btn btn-sm bg-red-600 hover:bg-red-700 text-white">Delete</button>
            </div>
          </div>
        </div>
        
        <div *ngIf="events.length === 0" class="card p-8 text-center text-secondary-500">
          <p class="text-lg mb-2">No special events defined</p>
          <p class="text-sm">Create events to block timeslots for interdisciplinary seminars, assemblies, or other fixed activities</p>
        </div>
      </div>
    </div>
  `
})
export class SpecialEventsComponent implements OnInit {
    private api = inject(ApiService);
    private http = inject(HttpClient);

    events: SpecialEvent[] = [];
    studentGroups: StudentGroup[] = [];
    rooms: Room[] = [];
    lecturers: Lecturer[] = [];
    parentGroupIds: Set<number> = new Set();
    groupById: Map<number, StudentGroup> = new Map();

    showAddForm = false;
    editingEvent: SpecialEvent | null = null;
    capacityError = '';
    formData = {
        name: '',
        description: '',
        dayOfWeek: 'WEDNESDAY',
        startTime: '10:00',
        durationHours: 2,
        roomId: null as number | null,
        lecturerId: null as number | null,
        online: false,
        studentGroupIds: [] as number[]
    };

    ngOnInit() {
        this.loadEvents();
        this.api.getStudentGroups().subscribe({
            next: (g) => {
                this.studentGroups = g;
                this.refreshParentGroups();
            }
        });
        this.api.getRooms().subscribe({ next: (r) => this.rooms = r });
        this.api.getLecturers().subscribe({ next: (l) => this.lecturers = l });
    }

    loadEvents() {
        this.http.get<SpecialEvent[]>('http://localhost:8080/api/v1/special-events').subscribe({
            next: (events) => this.events = events
        });
    }

    isGroupSelected(groupId: number): boolean {
        return this.getNormalizedSelectedGroupIds().includes(groupId);
    }

    toggleGroup(groupId: number) {
        if (!this.isParentGroup(groupId)) {
            return;
        }
        const normalizedIds = this.getNormalizedSelectedGroupIds();
        const index = normalizedIds.indexOf(groupId);
        if (index > -1) {
            normalizedIds.splice(index, 1);
            this.formData.studentGroupIds = normalizedIds;
            this.capacityError = '';
        } else {
            if (this.willExceedCapacityWith(groupId)) {
                const room = this.getSelectedRoom();
                if (room) {
                    this.capacityError =
                        `Selected groups exceed room capacity (${room.capacity}). Choose a larger room or fewer groups.`;
                }
                return;
            }
            normalizedIds.push(groupId);
            this.formData.studentGroupIds = normalizedIds;
            this.capacityError = '';
        }
    }

    saveEvent() {
        if (this.isCapacityExceeded()) {
            const room = this.getSelectedRoom();
            if (room) {
                this.capacityError =
                    `Selected groups exceed room capacity (${room.capacity}). Choose a larger room or fewer groups.`;
            }
            return;
        }

        const payload = {
            ...this.formData,
            studentGroupIds: this.getNormalizedSelectedGroupIds()
        };
        const obs = this.editingEvent
            ? this.http.put<SpecialEvent>(`http://localhost:8080/api/v1/special-events/${this.editingEvent.id}`, payload)
            : this.http.post<SpecialEvent>('http://localhost:8080/api/v1/special-events', payload);

        obs.subscribe({
            next: () => { this.loadEvents(); this.cancelEdit(); },
            error: (err) => alert('Failed: ' + (err.error?.message || 'Unknown error'))
        });
    }

    editEvent(event: SpecialEvent) {
        this.editingEvent = event;
        this.formData = {
            name: event.name,
            description: event.description || '',
            dayOfWeek: event.dayOfWeek,
            startTime: event.startTime,
            durationHours: event.durationHours,
            roomId: event.roomId,
            lecturerId: event.lecturerId,
            online: event.online,
            studentGroupIds: (event.studentGroupIds || []).map(id => Number(id)).filter(id => Number.isFinite(id))
        };
        this.formData.studentGroupIds = this.formData.studentGroupIds.filter(id => this.parentGroupIds.has(id));
        this.showAddForm = true;
    }

    toggleActive(event: SpecialEvent) {
        this.http.put<SpecialEvent>(`http://localhost:8080/api/v1/special-events/${event.id}/toggle-active`, {}).subscribe({
            next: () => this.loadEvents()
        });
    }

    deleteEvent(id: number) {
        if (confirm('Delete this special event?')) {
            this.http.delete(`http://localhost:8080/api/v1/special-events/${id}`).subscribe({
                next: () => this.loadEvents()
            });
        }
    }

    cancelEdit() {
        this.showAddForm = false;
        this.editingEvent = null;
        this.capacityError = '';
        this.formData = {
            name: '',
            description: '',
            dayOfWeek: 'WEDNESDAY',
            startTime: '10:00',
            durationHours: 2,
            roomId: null,
            lecturerId: null,
            online: false,
            studentGroupIds: []
        };
    }

    getSelectedRoom(): Room | undefined {
        if (this.formData.online || this.formData.roomId == null) {
            return undefined;
        }
        return this.rooms.find(r => r.id === this.formData.roomId);
    }

    getSelectedPopulation(): number {
        return this.getNormalizedSelectedGroupIds()
            .map(id => this.groupById.get(id))
            .filter((group): group is StudentGroup => !!group)
            .reduce((sum, g) => sum + (Number(g.size) || 0), 0);
    }

    isCapacityExceeded(): boolean {
        const room = this.getSelectedRoom();
        if (!room) {
            return false;
        }
        return this.getSelectedPopulation() > room.capacity;
    }

    willExceedCapacityWith(groupId: number): boolean {
        const room = this.getSelectedRoom();
        if (!room) {
            return false;
        }
        const group = this.groupById.get(groupId);
        if (!group) {
            return false;
        }
        return this.getSelectedPopulation() + (group.size || 0) > room.capacity;
    }

    get parentGroups(): StudentGroup[] {
        return this.studentGroups.filter(g => g.parentGroupId == null);
    }

    private isParentGroup(groupId: number): boolean {
        return this.parentGroupIds.has(groupId);
    }

    private refreshParentGroups() {
        this.groupById = new Map(
            this.studentGroups.map(g => [Number(g.id), g] as [number, StudentGroup])
        );
        this.parentGroupIds = new Set(
            this.studentGroups
                .filter(g => g.parentGroupId == null)
                .map(g => Number(g.id))
        );
        this.formData.studentGroupIds = this.getNormalizedSelectedGroupIds();
    }

    private getNormalizedSelectedGroupIds(): number[] {
        const selected = (this.formData.studentGroupIds || [])
            .map(id => Number(id))
            .filter(id => Number.isFinite(id))
            .filter(id => this.parentGroupIds.has(id));
        return [...new Set(selected)];
    }

    onRoomChanged() {
        this.capacityError = this.isCapacityExceeded()
            ? 'Selected groups exceed room capacity. Choose a larger room or fewer groups.'
            : '';
    }

    onOnlineChanged() {
        if (this.formData.online) {
            this.capacityError = '';
        } else {
            this.onRoomChanged();
        }
    }
}
