
import csv
import shutil

# Source and destination paths
input_file = 'csv_files/rooms.csv'
output_file = 'csv_files/rooms_upgraded.csv'

# Missing features to distribute
missing_features = [
    "Legal Research Station", "Dance Floor", "Wet Lab", "Biology Lab", "Chemistry Lab",
    "Physics Lab", "Art Studio", "Music Studio", "3D Printers", "CAD Workstations",
    "Recording Studio", "Medical Equipment", "Workshop Equipment", "Gym Equipment",
    "Server Room", "Network Lab", "Language Lab", "Video Conferencing",
    "Microphone System", "Smart Board", "Air Conditioning", "Projector", "Auditorium Seating"
]

# Mapping of Zone to Features (Logic: Add appropriate features to relevant zones)
zone_feature_map = {
    "Law Building": ["Legal Research Station", "Smart Board", "Video Conferencing", "Microphone System", "Projector", "Air Conditioning"],
    "Sports Complex": ["Gym Equipment", "Dance Floor", "Microphone System", "Air Conditioning"],
    "Science Tower": ["Wet Lab", "Biology Lab", "Chemistry Lab", "Physics Lab", "Medical Equipment", "3D Printers", "Projector", "Air Conditioning"],
    "Arts and Humanities": ["Art Studio", "Music Studio", "Recording Studio", "Language Lab", "Projector", "Air Conditioning", "Microphone System"],
    "Engineering Complex": ["CAD Workstations", "Workshop Equipment", "3D Printers", "Projector", "Air Conditioning", "Smart Board"],
    "ICT Center": ["Server Room", "Network Lab", "Computer Lab", "Projector", "Air Conditioning", "Video Conferencing"],
    "Medical Sciences Building": ["Medical Equipment", "Wet Lab", "Biology Lab", "Projector", "Air Conditioning"],
    "Agriculture Building": ["Biology Lab", "Wet Lab", "Workshop Equipment", "Projector", "Air Conditioning"],
    "BucoDEL Labs": ["Computer Lab", "Network Lab", "Server Room", "3D Printers", "Projector", "Air Conditioning"],
    "Education Block": ["Smart Board", "Projector", "Video Conferencing", "Microphone System", "Air Conditioning"],
    "Business School": ["Smart Board", "Video Conferencing", "Microphone System", "Projector", "Air Conditioning"],
    "Social Sciences Wing": ["Projector", "Whiteboard", "Air Conditioning"],
    "SAT Computer Labs": ["Computer Lab", "Projector", "Air Conditioning", "CAD Workstations"],
    "SAT Normal Classes": ["Projector", "Whiteboard", "Air Conditioning"],
    "SAT Lecture Theatres": ["Microphone System", "Auditorium Seating", "Projector", "Air Conditioning", "Video Conferencing"],
     "BBS Lecture Theatres": ["Microphone System", "Auditorium Seating", "Projector", "Air Conditioning", "Video Conferencing"],
    "NH Computer Labs": ["Computer Lab", "Projector", "Air Conditioning"],
    "NH Normal Classes": ["Projector", "Whiteboard", "Air Conditioning"]
}

updated_rows = []

with open(input_file, 'r', newline='') as f:
    reader = csv.DictReader(f)
    fieldnames = reader.fieldnames
    
    for row in reader:
        zone = row['zone_name']
        current_features = row['features'].split('|') if row['features'] else []
        
        # Add general features to ALL rooms if missing (Air Con, Projector - basic stuff)
        if "Air Conditioning" not in current_features:
            current_features.append("Air Conditioning")
        
        # Add specialized features based on Zone
        if zone in zone_feature_map:
            allowed_features = zone_feature_map[zone]
            # Add features from the allowed list if likely to handle them
            # To be safe, let's add ALL allowed features to at least SOME rooms in the zone
            # For now, let's simple-add them to relevant rooms to ensure coverage
            
            # Simple strategy: Add ALL mapped features to the room's feature list
            # This ensures maximum compatibility. User said "just handle features gap".
            for feat in allowed_features:
                if feat not in current_features:
                    current_features.append(feat)

        row['features'] = '|'.join(current_features)
        updated_rows.append(row)

# Create a few SUPER ROOMS to handle weird combos (like "Dance Floor" + "Chem Lab" if any)
# Actually, the user wants to keep capacity issues for themselves.
# Prioritize coverage.

# Write output
with open(output_file, 'w', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(updated_rows)

print(f"Successfully created {output_file} with upgraded features.")
