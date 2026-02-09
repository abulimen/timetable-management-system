import csv
import random

# Paths
COURSES_PATH = 'docs/SCRAPED DATA/REPORT/courses.csv'
LECTURERS_PATH = 'docs/SCRAPED DATA/REPORT/export_lecturers_2026-02-07-02-19.csv'

# Load valid lecturers for variety
valid_lecturers = []
with open(LECTURERS_PATH, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        if row.get('email'):
            valid_lecturers.append(row['email'].strip())

print(f"Loaded {len(valid_lecturers)} lecturers for variety")

# Read aggregated CSV and split back into separate rows
expanded_rows = []
fieldnames = []

with open(COURSES_PATH, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    fieldnames = reader.fieldnames
    
    for row in reader:
        # Split pipe-separated groups
        groups = [g.strip() for g in row.get('student_group_names', '').split('|') if g.strip()]
        features = [f.strip() for f in row.get('required_features', '').split('|') if f.strip()]
        zones = [z.strip() for z in row.get('allowed_zones', '').split('|') if z.strip()]
        
        if not groups:
            # No groups, keep single row
            expanded_rows.append(row)
        else:
            # Create separate row for each group with variety in lecturers/features
            for i, group in enumerate(groups):
                new_row = row.copy()
                new_row['student_group_names'] = group
                
                # Add lecturer variety - different lecturer per group
                if valid_lecturers:
                    new_row['lecturer_email'] = random.choice(valid_lecturers)
                
                # Distribute features - some rows get more, some get less
                if features:
                    num_features = random.randint(1, min(3, len(features)))
                    selected_features = random.sample(features, num_features)
                    new_row['required_features'] = '|'.join(selected_features)
                
                # Pick one zone per row
                if zones:
                    new_row['allowed_zones'] = random.choice(zones)
                
                expanded_rows.append(new_row)

# Write back expanded CSV
with open(COURSES_PATH, 'w', encoding='utf-8', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(expanded_rows)

print(f"Expanded {COURSES_PATH} to {len(expanded_rows)} rows (un-aggregated with lecturer variety)")
